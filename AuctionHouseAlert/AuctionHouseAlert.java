import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AuctionHouseAlert {

    //comparator for uuid heap
    private static class uuidComparator implements Comparator<String> {
        //compares based on the price
        @Override
        public int compare(String o1, String o2) {
            return priceMap.get(o1) - priceMap.get(o2);
        }
    }

    //constant to display the maximum number of items that could be displayed at once
    private static final int MAX_ITEMS_DISPLAYED = 10;

    private static Queue<String> uuid;
    private static Map<String, Integer> priceMap;

    //either 1 or 2 for number of attributes
    private static int mode;
    //name of the item to be searched
    private static String desiredItemName;
    //wanted attributes
    private static String desiredAttribute;
    private static String desiredAttribute2;
    //maximum price that will be alerted
    private static double priceWanted;
    //data usage
    private static int timeScanned;
    private static int totalTime;

    public static void main(String[] args) throws InterruptedException {

        //scanner for user input
        Scanner scanner = new Scanner(System.in);

        //loop until 1 or 2 is inputted
        while (mode != 1 && mode != 2) {
            
            System.out.print("How many Attributes? (1 or 2): ");

            String modeString = scanner.nextLine();

            //for non number input
            try {
                mode = Integer.parseInt(modeString);
            } catch (NumberFormatException e) {
            }
            if (mode != 1 && mode != 2){
                System.out.println("Enter 1 or 2");
            }
        }

        System.out.print("Item Name: ");
        desiredItemName = format(scanner.nextLine());

        System.out.print("Attribute with Lvl (Roman Numerals): ");
        desiredAttribute = format(scanner.nextLine());
        desiredAttribute2 = "";

        if (mode == 2) {
            System.out.print("Second Attribute with Lvl (Roman Numerals): ");
            desiredAttribute2 = format(scanner.nextLine());
        }

        System.out.print("Max Price to Alert at: ");
        String priceTarget = scanner.nextLine();

        priceWanted = convertToNumber(priceTarget);

        System.out.println();
        System.out.println("Loading...");

        timeScanned = 0;
        totalTime = 0;
    
        scanner.close();

        //loop forever
        while (true){
            //reset uuid heap and priceMap every scan
            uuid = new PriorityQueue<>(new uuidComparator());
            priceMap = new HashMap<>();

            //for time to take
            long time = System.nanoTime();

            //process the first page first to get the maximum number of pages
            PageReaderThread firstThread = new PageReaderThread("https://api.hypixel.net/skyblock/auctions?key=&page=0");
            firstThread.start();
            firstThread.join();
            
            //List of threads to join later
            List<PageReaderThread> threads = new ArrayList<>();
            threads.add(firstThread);
            //create threads to go all of the pages
            for (int i = 0; i < firstThread.maxPages; i++){
                PageReaderThread newThread = new PageReaderThread("https://api.hypixel.net/skyblock/auctions?key=&page=" + (threads.size()));
                newThread.start();
                threads.add(newThread);
            }
             
            //join the threads so that all threads are processed first
            for (PageReaderThread thread : threads){
                try {
                    thread.join();
                } 
                catch (InterruptedException e) {
                }
            }

            //update data
            timeScanned++;
            long timeElapsed = (System.nanoTime() - time) / 1000000000;
            totalTime += timeElapsed;
   
            displayInfoText(timeElapsed);
        }
    }

    //extended thread class to view all pages
    private static class PageReaderThread extends Thread {

        //url for the PageReaderThread to process
        private final String url;
        //text of the page
        String page = "";
        //for the initial page to get the maximum number of pages
        int maxPages = -1;

        //constuctor
        public PageReaderThread(String url){
            this.url = url;
        }

        @Override
        public void run() {
            BufferedReader reader;
            String line;
            StringBuilder responseContent = new StringBuilder();
            HttpURLConnection connection = null;

            try {
                @SuppressWarnings("deprecation")
                URL url = new URL(this.url);
                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
            }
            catch (IOException e) {
            }
            finally {
                page = responseContent.toString();
                try{
                    connection.disconnect();
                }
                catch(NullPointerException NPE){
                }
            }

            //process the data
            if (!page.contains("{\"success\":false,\"cause\":\"Page not found\"}")){
                maxPages = getMaxPages(page);
                //process the page if it still contains the desiredItemName
                //synchronize with uuid to prevent data races
                
                while (page.contains(desiredItemName)) {
                    page = findWhereUUIDStarts(page, page.indexOf(desiredItemName),
                            desiredItemName);

                    String currentItemLore = getLore(page);

                    // 1 attribute search
                    if (mode == 1 && currentItemLore.contains(desiredAttribute)
                            && checkForExactAttributeDesired(currentItemLore, desiredAttribute)
                            && isBin(page)) {
                        synchronized (uuid) {
                        priceMap.put(getUUID(page), getPrice(page));
                        uuid.add(getUUID(page));
                        }
                    }
                    // 2 attribute search
                    else if (mode == 2 && currentItemLore.contains(desiredAttribute)
                            && currentItemLore.contains(desiredAttribute2) &&
                            checkForExactAttributeDesired(currentItemLore, desiredAttribute)
                            &&
                            checkForExactAttributeDesired(currentItemLore, desiredAttribute2)
                            && isBin(page)) {
                            synchronized (uuid) {
                            priceMap.put(getUUID(page), getPrice(page));
                            uuid.add(getUUID(page));
                            }
                    }

                    // Going to next item
                    int nextItemIndex = page.indexOf("\"item_uuid\"");
                    page = page.substring(nextItemIndex + 10);
                }
                
                
            }
        }
    }

    //index of a attribute with lvl "I" might get "IV" also so check for that
    private static boolean checkForExactAttributeDesired(String itemLore, String desiredAttribute){
        char characterAfterItem = itemLore.charAt(itemLore.indexOf(desiredAttribute) + desiredAttribute.length());

        return characterAfterItem != 'V' && characterAfterItem != 'I';
    }

    private static void displayInfoText(long timeElapsed){
        //clears terminal
        System.out.print("\033c");

        System.out.println("_____________________________________________________________________");
        System.out.println("\nTime Elapsed: " + timeConvert((int) timeElapsed));
        System.out.println("Times Scanned: " + timeScanned);
        System.out.println("Average Time Elapsed: " + timeConvert((int) totalTime / timeScanned));
        System.out.println();
        System.out.println("Total Time Elapsed: " + timeConvert(totalTime));

        System.out.println();
        if (mode == 1)
            System.out.println(desiredItemName + " with " + desiredAttribute + "\n");
        else
            System.out.println(desiredItemName + " with " + desiredAttribute + " and " + desiredAttribute2);

        String text = "";

        //play sound if the first (cheapest) item is less than the price wanted
        if (!uuid.isEmpty() && priceMap.get(uuid.peek()) <= priceWanted) {
            playSound();
        }

        //uuid is empty
        if (uuid.isEmpty()) {
            text += "\nNone on auction house :(";
        }

        int size = Math.min(uuid.size(), MAX_ITEMS_DISPLAYED);
        for (int i = 0; i < size; i++) {
            String currentUUID = uuid.remove();
            text += "\n/viewauction " + currentUUID + "     |     " + addComma(priceMap.get(currentUUID));
        }

        if (size > MAX_ITEMS_DISPLAYED){
            text += "\n... " + (size - MAX_ITEMS_DISPLAYED) + " more items not displayed";
        }
        
        System.out.println(text);
        System.out.println("_____________________________________________________________________");
    }

    //adds a comma for the number parameter for better readability
    private static String addComma(int number) {

        String num = String.valueOf(number);
        int remainder = num.length() % 3;
        String text = "";

        switch (remainder) {
            case 0 -> {
                text += num.substring(0, 3);
                for (int i = 3; i < num.length(); i += 3) {
                    text += "," + num.substring(i, i + 3);
                }
            }
            case 1 -> {
                text += num.substring(0, 1);
                for (int i = 1; i < num.length(); i += 3) {
                    text += "," + num.substring(i, i + 3);
                }
            }
            case 2 -> {
                text += num.substring(0, 2);
                for (int i = 2; i < num.length(); i += 3) {
                    text += "," + num.substring(i, i + 3);
                }
            }
        }
        return text;
    }

    //plays a alert sound
    public static void playSound() {
        AudioInputStream audioStream = null;
        //try catch if file is not found 
        try {
            //file of sound file
            File file = new File("alertSound.wav");
            //instantiated audio player 
            audioStream = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException E) {
        } finally {
            try {
                audioStream.close();
            } catch (IOException | NullPointerException E) {
            }
        }
    }

    //Item names are case sensitive with capitalization with every word except for "of"
    //also capitalized roman numerals
    public static String format(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }

        //lower case the s parameter to format
        s = s.toLowerCase();
        //String to be returned
        String text = "";

        //Keep formating if there is a space, meaning more than 2 words are in the string
        while (s.contains(" ")) {
            //index of the end of the word
            int spaceIndex = s.indexOf(" ");

            //check if the next word is "of"
            if (s.length() >= 3 && s.substring(0, 3).equals("of ")) {
                //add of as lowercase
                text += s.substring(0, 3);
            }
            else {
                //add the first charater as Uppercase then the rest as lowercase
                text += Character.toUpperCase(s.charAt(0));
                text += s.substring(1, spaceIndex + 1);
            }

            //remove the word that was just added for next loop
            s = s.substring(spaceIndex + 1);
        }

        //last word
        if (isRomanNumeral(s)){
            text += s.toUpperCase();
        }
        else {
            text += Character.toUpperCase(s.charAt(0));
                text += s.substring(1);
        }

        return text;
    }

    //formats the time, parameters is in seconds
    public static String timeConvert(int time) {
        // < 1 min
        if (time < 60) {
            return String.valueOf(time) + " sec";
        }
        // < 1 hr
        else if (time < 3600) {
            int min = time / 60;
            time %= 60;
            return min + " min " + time + " sec";
        }
        // < 1 day
        else if (time < 86400) {
            int hr = time / 3600;
            time %= 3600;
            int min = time / 60;
            time %= 60;
            return hr + " hr " + min + " min " + time + " sec";
        }
        // > 1 day
        else {
            int day = time / 86400;
            time %= 86400;
            int hr = time / 3600;
            time %= 3600;
            int min = time / 60;
            time %= 60;
            return day + " day " + hr + " hr " + min + " min " + time + " sec";
        }
    }

    private static boolean isRomanNumeral(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++){
            char curr = s.charAt(i);

            if (curr != 'i' && curr != 'v' && curr != 'x'){
                return false;
            }
        }
        return true;
    }

    //allows postfixs (b - billion, m - million, k - thousand)
    private static double convertToNumber(String s) {
        //result that will be returned, if an invalid String is inputted the default 0 will be returned
        double result;

        //last character of the parameter
        char lastChar = s.charAt(s.length() - 1);
        //the parameter without the last character
        String numberWithoutLast = s.substring(0, s.length() - 1);
        
        //try catch block for if an invalid postfix was used or an invalid String     
        try{
            //thousand
            if (lastChar == 'k') {
                result = Double.parseDouble(numberWithoutLast) * 1000;
            }
            //million
            else if (lastChar == 'm') {
                result = Double.parseDouble(numberWithoutLast) * 1000000;
            }
            //< 10 billion
            else if (lastChar == 'b' && Double.parseDouble(numberWithoutLast) < 10) {
                result = Double.parseDouble(numberWithoutLast) * 1000000000;
            }
            //greater than 10 billion, auction house has a 10 billion bid limit so set it to 10 billion
            else if (lastChar == 'b') {
                result = 10000000000.0;
            }
            //no postfix was inputted convert the string to a number 
            else {
                result = Double.parseDouble(s);
            }
        }
        //catch invalid string and null pointer and set to the default value
        catch (NumberFormatException | NullPointerException NFE) {
            result = 0;
        }

        return result;
    }

    public static int getMaxPages(String text) {
        if (text.isEmpty()) return 0;

        int index = text.indexOf("\"totalPages\":") + "\"totalPages\":".length();

        String result = "";
        while (text.charAt(index) != ','){
            result += text.charAt(index++);
        }
        try{
            return Integer.parseInt(result);
        }
        catch(NumberFormatException e){
            return 0;
        }
    }

    //returns the page number 
    public static String findWhereUUIDStarts(String text, int indexOfItemName, String desiredItemName) {
        int steps = 0;
        String s = text.substring(indexOfItemName);
        int indexOfUUID = Integer.MAX_VALUE;
        int newIndexOfItemName = s.indexOf(desiredItemName);

        while (indexOfUUID > newIndexOfItemName) {
            steps += 25;
            if (indexOfItemName - steps <= 0) return text.substring(0);
            s = text.substring(indexOfItemName - steps);
            indexOfUUID = s.indexOf("\"uuid\"");
            newIndexOfItemName = s.indexOf(desiredItemName);
        }

        return text.substring(indexOfItemName - steps);
    }

    public static String getUUID(String s) {
        int itemUUIDIndex = s.indexOf("\"uuid\"") + 8;
        int lengthOfUUID = 32;
        return s.substring(itemUUIDIndex, itemUUIDIndex + lengthOfUUID);
    }

    public static int getPrice(String s) {
        int itemPriceIndex = s.indexOf("starting_bid") + 14;
        int itemPriceEndIndex = itemPriceIndex;
        String currentPageAfterCurrentItemPrice = s.substring(itemPriceIndex);

        int len = currentPageAfterCurrentItemPrice.length();
        for (int i = 0; i < len; i++) {
            if (currentPageAfterCurrentItemPrice.charAt(i) == ',') {
                itemPriceEndIndex += i;
                break;
            }
        }
        return Integer.parseInt(s.substring(itemPriceIndex, itemPriceEndIndex));
    }

    public static String getLore(String s) {
        int itemLoreIndex = s.indexOf("item_lore") + 12;
        int itemLoreEndIndex = itemLoreIndex;
        String currentPageAfterCurrentItemLore = s.substring(itemLoreIndex);

        int len = currentPageAfterCurrentItemLore.length();
        for (int i = 0; i < len; i++) {
            if (currentPageAfterCurrentItemLore.charAt(i) == '\"') {
                itemLoreEndIndex += i;
                break;
            }
        }
        return s.substring(itemLoreIndex, itemLoreEndIndex);
    }

    //we only want items that lists bin as true
    public static boolean isBin(String s) {
        //gets the first character of the bin either 't' for true and 'f' for false
        int binIndex = s.indexOf("\"bin\"") + 6;
        return s.charAt(binIndex) == 't';
    }
}