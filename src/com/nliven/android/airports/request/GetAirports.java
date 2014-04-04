
package com.nliven.android.airports.request;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;
import com.nliven.android.airports.AirportApplication;
import com.nliven.android.airports.biz.model.Airport;
import com.nliven.android.airports.events.GetAirportsCompletedEvent;

/**
 *  Uses the "async-http-request" library to make a REST request.  If a successful
 *  response is received, it uses GSON to parse the JSON string and stores the
 *  data into the Sqlite CacheDb using the GreenDao ORM relational database tool.
 * 
 * @author matthew.woolley
 */
public class GetAirports {

    private static final String TAG = GetAirports.class.getSimpleName();
    
    private static final String REQUEST_URL = "http://airports.pidgets.com/v1/airports?state=California&format=json";

    private static AsyncHttpClient client = new AsyncHttpClient();
    
    /*
     * GSON-related setup...
     */
    private static AirportDeserializer gsonAirportDeserializer = new GetAirports.AirportDeserializer();
    private static GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(Airport[].class, gsonAirportDeserializer);
    private static Gson gson = gsonBuilder.create();
    
    public static void execute() {

        /*
         * Performs the REST request and handles a Failed or Success callback accordingly.
         * We then publish/post a Event so any Listener/Subscriber gets notification
         * when the request has completed, whether it failed or not.
         */
        client.get(REQUEST_URL, new TextHttpResponseHandler() {

            @Override
            public void onFailure(int statusCode, Throwable error, String content) {

                // Publish an Otto Event. Any Subscriber will listen for this
                // Event and will update accordingly.  Notice we set the success boolean 
                // to 'false'
                AirportApplication.getEventBus().post(
                        new GetAirportsCompletedEvent(false, statusCode));
            }
            

            @Override
            public void onSuccess(int statusCode, String content) {

                /*
                 * 1. Parse the JSON String using GSON.  The custom GSON Deserializer implementation
                 *    will also create an instance of the Airport entity and save it to the Db. 
                 * 2. Publish an Otto Event. Any Subscriber (i.e. a View or Activity) 
                 *    will listen for this event and will update accordingly.
                 */
                
                //1. Clear the Airport Db Table
                AirportApplication.getAirportDao().deleteAll();

                //2. Parse the JSON String using GSON.  The custom GSON Deserializer implementation
                //   will also create an instance of the Airport entity and save it to the Db.                 
                //gsonBuilder.registerTypeAdapter(Airport[].class, gsonAirportDeserializer);
                //Gson gson = gsonBuilder.create();
                Airport[] airports = gson.fromJson(content, Airport[].class);
                
                /*
                 * Set to null for immediate garbage collection, b/c we dont return any object from here.
                 * We expect the caller of this request to have implemented a Subscriber for the
                 * Otto post below.
                 * NOTE: We *could* also publish this as a new field in the GetAirportsCompletedEvent below,
                 * but we want the Subscriber (i.e. the MainActivity) to exercise GreenDao's "query" 
                 * functionality, etc.  Plus, the activity should really have its own copy of the 
                 * Airports and we probably shouldnt pass this instance of the airport list around (i.e.
                 * memory leakage.)
                 */
                airports = null;
                    
                //3. Publish an Otto Event. Any Subscriber (i.e. a View or Activity) 
                //   will listen for this event and will update accordingly.
                AirportApplication.getEventBus().post(new GetAirportsCompletedEvent(true, statusCode));
           
            }                               
        });
    }

    /**
     * Using a custom JsonDeserializer b/c I havent been able to find a way to mark the 
     * Airport Model properties with an annotation so GSON can do this
     * under-the-covers/automatically.  
     * 
     * Otherwise, we could create a separate "AirportDTO" object class, then 
     * map it to the "Airport" entity/model class automatically using GSON and not
     * have to use a Custom JsonDeserializer.
     */
    private static class AirportDeserializer implements JsonDeserializer<Airport[]>{

        @Override
        public Airport[] deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {

            JsonArray airports = (JsonArray)json;
            Airport[] airportEntities = new Airport[airports.size()];

            for (int i=0; i<airports.size(); i++) {

                JsonObject jObject = (JsonObject) airports.get(i);

                //Create an instance of the Airport Entity
                Airport ap = new Airport();
                ap.setCode(jObject.get("code").getAsString());
                ap.setIcao(jObject.get("icao").getAsString());
                ap.setName(jObject.get("name").getAsString());
                ap.setCity(jObject.get("city").getAsString());
                ap.setState(jObject.get("state").getAsString());
                ap.setLatitude(jObject.get("lat").getAsDouble());
                ap.setLongitude(jObject.get("lon").getAsDouble());
                ap.setRunwayLength((!jObject.get("runway_length").isJsonNull()) ? jObject.get("runway_length").getAsInt() : null);
                ap.setIcao(jObject.get("url").getAsString());
                
                //Save the Airport entity to the database
                AirportApplication.getAirportDao().insert(ap);
                
                //Add to collection.
                airportEntities[i] = ap;
            }
            return airportEntities;
        }
    }    
}
