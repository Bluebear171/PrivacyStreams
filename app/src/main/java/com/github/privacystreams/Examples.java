package com.github.privacystreams;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.github.privacystreams.accessibility.BrowserSearch;
import com.github.privacystreams.audio.Audio;
import com.github.privacystreams.audio.AudioOperators;
import com.github.privacystreams.commons.comparison.Comparators;
import com.github.privacystreams.commons.items.ItemsOperators;
import com.github.privacystreams.commons.list.ListOperators;
import com.github.privacystreams.commons.statistic.StatisticOperators;
import com.github.privacystreams.commons.string.StringOperators;
import com.github.privacystreams.commons.time.TimeOperators;
import com.github.privacystreams.communication.Call;
import com.github.privacystreams.communication.Contact;
import com.github.privacystreams.communication.Message;
import com.github.privacystreams.core.Callback;
import com.github.privacystreams.core.Item;
import com.github.privacystreams.core.MStream;
import com.github.privacystreams.core.UQI;
import com.github.privacystreams.core.exceptions.PSException;
import com.github.privacystreams.core.purposes.Purpose;
import com.github.privacystreams.device.BluetoothDevice;
import com.github.privacystreams.device.DeviceOperators;
import com.github.privacystreams.device.WifiAp;
import com.github.privacystreams.image.Image;
import com.github.privacystreams.image.ImageOperators;
import com.github.privacystreams.location.Geolocation;
import com.github.privacystreams.location.GeolocationOperators;
import com.github.privacystreams.location.LatLng;
import com.github.privacystreams.notification.Notification;
import com.github.privacystreams.storage.DropboxOperators;
import com.github.privacystreams.utils.Globals;
import com.github.privacystreams.utils.StorageUtils;

import java.util.List;

/**
 * Some examples of using PrivacyStreams for accessing and processing personal data.
 */
public class Examples {
    private UQI uqi;
    private Purpose purpose;

    public Examples(Context context) {
        this.uqi = new UQI(context);
        this.purpose = Purpose.TEST("Examples"); // Developers should replace with actual purpose in real apps.
    }

    /** Get the location metadata of all local images. */
    public void getImageMetadata() {
        uqi.getData(Image.getFromStorage(), purpose) // get a stream of local images.
                .setField("lat_lng", ImageOperators.getLatLng("image_data")) // create a new field "lat_lng" from the "image_data" field using `getLatLng` operator.
                .setField("image_path", ImageOperators.getFilepath("image_data")) // create a new field "image_path" from the the "image_data" field using `getFilepath` operator.
                .project("lat_lng", "image_path") // only keep the "lat_lng" field and "image_path" field in each item.
                .forEach(new Callback<Item>() {
                    @Override
                    protected void onInput(Item input) { // call back with each item.
                        if (input == null) return;
                        LatLng latLng = input.getValueByField("lat_lng"); // get the "lat_lng" field from the item
                        String imagePath = input.getValueByField("image_path"); // get the "image_path" from the item
                        double lat = latLng.getLatitude();
                        double lng = latLng.getLongitude();
                        System.out.println("image: " + imagePath + ", lat:" + lat + ", lng:" + lng);
                    }
                });
    }

    /** Take a photo with camera and get the new photo's path. */
    public void takePhoto() {
        try {
            Item photoItem = uqi.getData(Image.takeFromCamera(), purpose) // get an SStream of image from camera, user will need to take a photo here.
                    .setField("photo_path", ImageOperators.getFilepath("image_data")) // create a field "photo_path" from "image_data" field using `getFilepath` operator.
                    .asItem(); // get the photo item.
            String photoPath = photoItem.getValueByField("photo_path"); // get the value of "photo_path" field.

//            // You can also use `getField` instead of `asItem`.
//            String photoPath = uqi.getData(Image.takeFromCamera(), purpose)
//                    .setField("photo_path", ImageOperators.getFilepath("image_data"))
//                    .getField("photo_path"); // get the "photo_path" field.

            System.out.println("The new photo's path is " + photoPath);
        } catch (PSException e) {
            e.printStackTrace();
        }
    }

    /** Record audio in next 10 seconds and get loudness. */
    public void getCurrentLoudness() {
        uqi.getData(Audio.record(10*1000), purpose) // get an audio stream from microphone, the only item is an 10-second audio recorded from microphone
                .setField("loudness", AudioOperators.calcLoudness("audio_data")) // create a field "loudness" based on "audio_data" field using `calcLoudness` operator.
                .ifPresent("loudness", new Callback<Double>() {
                    @Override
                    protected void onInput(Double input) { // get the value of "loudness" field and callback.
                        System.out.println("Current loudness is " + input + " dB.");
                    }
                });
    }

    /** Monitor fine-grained location once per second and check whether it's in an square region. */
    public void monitorLocationUpdates() {
        double minLat = 40.0, minLng = -180.0, maxLat = 40.1, maxLng = -180.1; // the square region.

        uqi.getData(Geolocation.asUpdates(1000, Geolocation.LEVEL_EXACT), purpose) // get a live stream of location updates, the location granularity is "EXACT".
                .setField("inRegion", GeolocationOperators.inSquare("lat_lng", minLat, minLng, maxLat, maxLng))
                .forEach(new Callback<Item>() {
                    @Override
                    protected void onInput(Item location) { // callback for each item.
                        LatLng latLng = location.getValueByField("lat_lng"); // get the value of "lat_lng" field.
                        Boolean inRegion = location.getValueByField("inRegion"); // get the value of "inRegion" field.

                        System.out.println("lat=" + latLng.getLatitude() + ", lng=" + latLng.getLongitude());
                        if (inRegion) System.out.println("the location is in the square region.");
                    }
                });
    }

    /** Get current city-level location. */
    public void getCityLocation() {
        try {
            LatLng latLng = uqi.getData(Geolocation.asCurrent(Geolocation.LEVEL_CITY), purpose) // get an SStream of current location, the location granularity is "CITY".
                    .getField("lat_lng"); // get the "lat_lng" field of current location.
            System.out.println("Current location: lat=" + latLng.getLatitude() + ", lng=" + latLng.getLongitude());
        } catch (PSException e) {
            e.printStackTrace();
        }

    }

    /** Get a stream of notifications and print each notification. */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void printNotification() {
        uqi.getData(Notification.asUpdates(), purpose).debug();
    }

    /** Monitor user's browser search events. */
    public void getBrowserSearchEvents() {
        uqi.getData(BrowserSearch.asUpdates(), purpose) // get a live stream of BrowserSearch events.
                .forEach("text", new Callback<String>() { // callback with the "text" value for each event.
                    @Override
                    protected void onInput(String input) {
                        System.out.println("The user searched: " + input);
                    }
                });
    }

    /** Get the phone number of the contact with the most calls in recent 1 year. */
    public void getContactWithMostCalls() {
        try {
            String phoneNum = uqi.getData(Call.getLogs(), purpose) // get a stream of call logs.
                    .filter(TimeOperators.recent("timestamp", 365*24*60*60*1000L)) // keep the items whose "timestamp" field is a time in recent 365 days.
                    .groupBy("contact") // group by "contact" field.
                    .setGroupField("#calls", StatisticOperators.count()) // create a new field "#calls" as the count of grouped items in each group
                    .select(ItemsOperators.getItemWithMax("#calls")) // select the item with the max "#calls" value.
                    .getField("contact"); // get the value of "contact" field
            System.out.println("The phone number with the most calls: " + phoneNum);
        } catch (PSException e) {
            e.printStackTrace();
        }
    }

    /** Get the names of contacts that the user had phone call with. */
    public void getCalledNames() {
        try {
            List<String> calledPhoneNumbers = uqi.getData(Call.getLogs(), purpose) // get call logs
                    .asList("contact"); // collect the value of "contact" field in each item to a list
            List<String> calledNames = uqi.getData(Contact.getAll(), purpose) // get all contacts
                    .filter(ListOperators.intersects("phones", calledPhoneNumbers.toArray())) // keep the contacts whose "phones" field has intersection with `calledPhoneNumbers`
                    .asList("name"); // collect the value of "name" field to a list
            System.out.println("The user had phone call with: " + calledNames);
        } catch (PSException e) {
            e.printStackTrace();
        }
    }

    /** Calculate total number of calls and length of calls for each contact in call log. */
    public void getNumCallsEachContact() {
        try {
            List<Item> items = uqi.getData(Call.getLogs(), purpose) // get call logs
                    .groupBy("contact") // group by the "contact" field
                    .setGroupField("num_of_calls", StatisticOperators.count()) // create a "num_of_calls" field as the count of grouped items in each group
                    .setGroupField("length_of_calls", StatisticOperators.sum("duration")) // create a "length_of_calls" field as the sum of "duration" fields in the grouped items.
                    .project("contact", "num_of_calls", "length_of_calls") // keep three fields: "contact", "num_of_calls", "length_of_calls"
                    .asList(); // collect all items to a list.
            for (Item item : items) {
                String contact = item.getValueByField("contact");
                Integer numCalls = item.getValueByField("num_of_calls");
                Double lenCalls = item.getValueByField("length_of_calls");
            }
        } catch (PSException e) {
            e.printStackTrace();
        }
    }

    /** Get all received SMS messages that contains "Alert" substring and hashed phone number. */
    public void searchReceivedMessages() {
        try {
            List<Item> items = uqi.getData(Message.getAllSMS(), purpose) // get all SMS messages.
                    .filter("type", Message.TYPE_RECEIVED) // keep the messages whose "type" field is "received".
                    .filter(StringOperators.contains("content", "Alert")) // keep the messages whose "content" field contains "Alert" substring
                    .setField("hashed_phone", StringOperators.sha1("contact")) // create a new field "hashed_phone" as the sha1 hash of "contact".
                    .project("content", "hashed_phone") // keep the "content", "hashed_phone" fields in each item.
                    .asList(); // collect all items to a list.
            for (Item item : items) {
                String msgContent = item.getValueByField("content");
                String hashedPhone = item.getValueByField("hashed_phone");
                System.out.println("Received message: " + msgContent);
                System.out.println("The hashed phone number: " + hashedPhone);
            }
        } catch (PSException e) {
            e.printStackTrace();
        }
    }

    /** Monitor messages in IM apps (WhatsApp, Facebook Messenger, etc.). */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void monitorMessagesIM(){
        uqi.getData(Message.asUpdatesInIM(), purpose) // get a live stream of IM message updates.
                .forEach(new Callback<Item>() {
                    @Override
                    protected void onInput(Item input) { // callback with each item.
                        String type = input.getValueByField("type"); // get the value of "type" field.
                        String content = input.getValueByField("content"); // get the value of "content" field.
                        String contact = input.getValueByField("contact"); // get the value of "contact" field.
                        if ("sent".equals(type)) {
                            System.out.println("Sent a message to " + contact + ": " + content);
                        }
                        else if ("received".equals(type)) {
                            System.out.println("Received a message from " + contact + ": " + content);
                        }
                    }
                });
    }

    /** Get the SSID of the connected Wifi Ap. */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void getConnectedWifiSSID() {
        uqi.getData(WifiAp.getScanResults(), purpose)  // get a stream of Wifi Ap scan results.
                .filter("connected", true)  // keep the items whose "connected" value is "true".
                .ifPresent("ssid", new Callback<String>() { // get the value of "ssid" field and callback.
                    @Override
                    protected void onInput(String input) {
                        System.out.println("Connected wifi SSID: " + input);
                    }
                });
    }

    /** Get the mac addresses of surrounding bluetooth devices. */
    public void getBluetoothMacAddresses() {
        try {
            List<String> macAddresses = uqi.getData(BluetoothDevice.getScanResults(), purpose)  // get a stream of bluetooth scan results.
                    .asList("mac_address");  // collect the "mac_address" field of all items to a list.
            System.out.println("Bluetooth devices: " + macAddresses);
        } catch (PSException e) {
            e.printStackTrace();
        }
    }

    /**
     * Upload the user's device id and current location to Dropbox.
     * This method requires [Dropbox configured](https://privacystreams.github.io/pages/install_dropbox_service.html).
     */
    public void uploadCurrentLocation() {
        Globals.DropboxConfig.accessToken = "Your Dropbox access token here.";
        Globals.DropboxConfig.leastSyncInterval = 60*60*1000L; // Upload to Dropbox once per hour.
        Globals.DropboxConfig.onlyOverWifi = false; // Upload only over WIFI.

        uqi.getData(Geolocation.asUpdates(10*60*1000L, Geolocation.LEVEL_EXACT), purpose) // get a live stream of exact geolocation, with a 10-minute interval.
                .setIndependentField("uuid", DeviceOperators.getDeviceId()) // create a new field "uuid" using `getDeviceId` operator.
                .project("lat_lng", "uuid") // keep the "lat_lng", "uuid" fields in each item.
                .forEach(DropboxOperators.<Item>uploadTo("Location.txt", true)); // upload the item to "Location.txt" file in Dropbox.
    }

    /**
     * Reuse the same stream for different operators.
     * Suppose we want to get:
     * 1. how many contacts' name is shorter than 10 characters;
     * 2. and how many contacts has more than 2 phone numbers.
     */
    public void reuseStream() {
        // Without reusing:
        try {
            int count1 = uqi.getData(Contact.getAll(), purpose) // get a stream of contacts
                    .setField("nameLen", StringOperators.length("name")) // set a new field "nameLen" as the length of "name" field value
                    .filter(Comparators.lt("nameLen", 10)) // keep the items whose "nameLen" field is less than 10
                    .count(); // get the count of items
            System.out.println("Number of contacts whose name is longer than 10 characters: " + count1);

            int count2 = uqi.getData(Contact.getAll(), purpose) // get a stream of contacts
                    .setField("numPhones", ListOperators.count("phones")) // set a new field "numPhones" as the length of "name" field value
                    .filter(Comparators.gt("numPhones", 2)) // keep the items whose "numPhones" field is greater than 2
                    .count(); // get the count of items
            System.out.println("Number of contacts who has more than 2 phone numbers: " + count2);
        } catch (PSException e) {
            e.printStackTrace();
        }

        // With reusing:
        try {
            MStream contactStream = uqi.getData(Contact.getAll(), purpose).reuse(2); // get a stream of contact for reusing twice.
            int count1 = contactStream.setField("nameLen", StringOperators.length("name")) // set a new field "nameLen" as the length of "name" field value
                    .filter(Comparators.lt("nameLen", 10)) // keep the items whose "nameLen" field is less than 10
                    .count(); // get the count of items
            System.out.println("Number of contacts whose name is longer than 10 characters: " + count1);

            int count2 = contactStream.setField("numPhones", ListOperators.count("phones")) // set a new field "numPhones" as the length of "name" field value
                    .filter(Comparators.gt("numPhones", 2)) // keep the items whose "numPhones" field is greater than 2
                    .count(); // get the count of items
            System.out.println("Number of contacts who has more than 2 phone numbers: " + count2);

        } catch (PSException e) {
            e.printStackTrace();
        }
    }

}
