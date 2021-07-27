package com.github.moritzhst.blitzi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.databinding.DataBindingUtil;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.github.moritzhst.blitzi.databinding.ActivityMainBinding;
import com.github.moritzhst.blitzi.model.AppDTO;
import com.github.moritzhst.blitzi.model.SanctionDTO;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

public class MainActivity extends AppCompatActivity {

    private AppDTO appDto;
    private SanctionDTO sanctionDTO;
    private Date lastOverpassRequest;

    private static Document sanctionMasterData;

    private static final Integer cINTERVAL = 1;
    private static final Integer cDELAY = 1;

    private static final Integer cSENSOR_READING = 200;
    private static final Integer cSENSOR_SAMPLE_SET = 5;

    private static final Integer cLOCATION_READ_INTERVAL = 1000;
    private static final Integer cOSM_RADIUS = 0;
    private static final Integer cMAX_OSM_RADIUS_TRIES = 5;

    private static final String cINSIDE = "inside";
    private static final String cOutside = "outside";

    private static final Long cOVERPASS_REQUEST_DELAY =(long) 10 /*Secs*/ * 1000 /*MS*/;

    private JSONObject currentLocation;

    private SensorManager sensorManager;
    private Sensor accSensor;

    private ArrayList<float[]> sampleSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //View-DTO initialisieren
        appDto = new AppDTO();
        sanctionDTO = new SanctionDTO();


        setContentView(R.layout.activity_main);


        //Binding initialisieren
        ActivityMainBinding binding = DataBindingUtil.setContentView(
                this, R.layout.activity_main);
        binding.setAppDTO(appDto);
        binding.setSanctionDTO(sanctionDTO);


        //Sensoren intiailisieren
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(new AccSensorEventHandler(), accSensor, cSENSOR_READING);
        sampleSet = new ArrayList<>();

        //Location-Sensor
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, new AppLocationHandler());
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, new AppLocationHandler());


        //Scheduler initialisieren
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
                this::update,
                cDELAY,
                cINTERVAL,
                TimeUnit.SECONDS);

        //Bußgeldstammdaten einlesen
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            AssetManager am = getBaseContext().getAssets();
            InputSource is = new InputSource(am.open("speed_limits.xml"));
            sanctionMasterData = builder.parse(is);
        } catch (ParserConfigurationException  | IOException | SAXException e) {
            e.printStackTrace();
        }

    }

    @SuppressLint("SetTextI18n")
    private void update() {
        setSanction(appDto);
        TextView current = (TextView)findViewById(R.id.points);
        current.setText(sanctionDTO.getPoints() + " Punkte");

        current = (TextView)findViewById(R.id.drivingBan);
        current.setText(sanctionDTO.getDrivingBan() + " Monate");

        current = (TextView)findViewById(R.id.money);
        current.setText(sanctionDTO.getMoney());
    }

    private void setSanction(AppDTO pAppDTO){
        if (pAppDTO == null){
            return;
        }
        System.out.println("Updating Sanctions");

        sanctionDTO.reset();

        Double currentSpeed = pAppDTO.currentSpeed.get();
        Long allowedSpeed = pAppDTO.allowedSpeed.get();

        String inOutSide = Boolean.TRUE.equals(pAppDTO.allowedSpeed.get() > 50)?  cOutside : cINSIDE;

        int speedDifference = (int) Math.floor(currentSpeed - allowedSpeed);

        NodeList sanctions = sanctionMasterData.getElementsByTagName("sanction");

        for (int i = 0; i < sanctions.getLength(); i++){
            Node currentNode = sanctions.item(i);
            NamedNodeMap attributes = currentNode.getAttributes();
            if (Integer.parseInt(attributes.getNamedItem("from").getNodeValue()) <= speedDifference && Integer.parseInt(attributes.getNamedItem("to").getNodeValue()) >= speedDifference && attributes.getNamedItem("place").getNodeValue().equals(inOutSide)){
                NodeList childNodes = currentNode.getChildNodes();
                for (int j = 0; j < childNodes.getLength(); j++){
                    switch (childNodes.item(j).getNodeName()){
                        case "money":
                            sanctionDTO.setMoney(childNodes.item(j).getFirstChild().getNodeValue().trim());
                            break;
                        case "points":
                            sanctionDTO.setPoints(Integer.parseInt(childNodes.item(j).getFirstChild().getNodeValue().trim()));
                            break;
                        case "drivingBan":
                            sanctionDTO.setDrivingBan(Integer.parseInt(childNodes.item(j).getFirstChild().getNodeValue().trim()));
                            break;
                    }
                }
                return;
            }
        }
    }


    class AccSensorEventHandler implements SensorEventListener {
        private double prevVelocity = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;

            if (cSENSOR_SAMPLE_SET.equals(sampleSet.size())) {
                updateVelocity();
                sampleSet.clear();
            }
            sampleSet.add(values);
        }

        private void updateVelocity() {
            double velocity = 0;
            double tmpVelocity;

            for (float[] values : sampleSet) {
                BigDecimal sum = new BigDecimal(0);

                for (float value : values) {
                    sum = sum.add(new BigDecimal(Math.pow(value, 2)));
                }
                velocity += Math.sqrt(sum.doubleValue());
            }
            //Durchschnittswert bilden
            velocity = velocity / cSENSOR_SAMPLE_SET;
            //In km/h umrechnen
            velocity = velocity * 3.6;

            //Delta bilden
            tmpVelocity = prevVelocity;
            prevVelocity = velocity;
            velocity = velocity - tmpVelocity;

            //Auf 2 Nachkommastellen runden
            //appDto.currentSpeed.set(Math.abs(Math.round(velocity * 100.0) / 100.0));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            System.out.println(accuracy);
        }
    }


    class AppLocationHandler implements LocationListener {
        public void onLocationChanged(Location location) {
            float currentVelocity = location.getSpeed();
            appDto.currentSpeed.set(Math.abs(Math.round(currentVelocity * 100.0) / 100.0) * 3.6);
            new OverpassRequest().execute(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

    class OverpassRequest extends AsyncTask<Location, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Location... location) {
            requestOverpass(location[0]);

            return true;
        }

        private void requestOverpass(Location location) {
            if (lastOverpassRequest != null && lastOverpassRequest.getTime() + cOVERPASS_REQUEST_DELAY > new Date().getTime()){
                return;
            }
            lastOverpassRequest = new Date();
            //http://overpass-api.de/api/interpreter?data=[out:json];way(around:5,54.07312,12.11435);out;
            StringBuilder buffer;
            Integer currentRadius = cOSM_RADIUS;
            Boolean finished = false;
            do {
                buffer = new StringBuilder();
                try {
                    URL url = new URL("http://overpass-api.de/api/interpreter?data=[out:json];way(around:" + currentRadius + "," + location.getLatitude() + "," + location.getLongitude() + ");out;");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    try {
                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                        int cur;

                        while ((cur = in.read()) != -1) {
                            buffer.append((char) cur);
                        }

                        finished = evaluateResponse(buffer);
                        currentRadius++;
                    } finally {
                        urlConnection.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            while (Boolean.FALSE.equals(finished) && currentRadius < cOSM_RADIUS + cMAX_OSM_RADIUS_TRIES );
        }

        private Boolean evaluateResponse(StringBuilder buffer) {
            try {
                JSONObject json = new JSONObject(buffer.toString());
                JSONArray elements = json.getJSONArray("elements");
                //Wir sollten nur ein Element rausziehen, wenn es mehr sind haben wir eh kein Plan welches richtig ist
                JSONObject cur = elements.getJSONObject(0);
                currentLocation = cur;
            } catch (JSONException e) {
                return false;
                //Woops. Nicht schlimm, es sollte ein Ort zwischengespeichert sein!
            }

            if (currentLocation == null){
                return true;
            }

            try {
                JSONObject tags = currentLocation.getJSONObject("tags");
                Double maxSpeed = tags.getDouble("maxspeed");
                appDto.allowedSpeed.set(maxSpeed.longValue());
            } catch (JSONException e) {
                //Tag maxSpeed nicht vorhanden, also anders ermitteln
            }

            return true;
        }
    }


}
