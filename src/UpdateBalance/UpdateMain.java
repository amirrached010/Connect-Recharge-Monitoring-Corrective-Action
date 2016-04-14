/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package UpdateBalance;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import org.apache.log4j.Logger;


/**
 *
 * @author omar.alfar
 */

public class UpdateMain {

    static Logger logger;
    static Properties properties;
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public static void main(String [] args)
    {
        logger = Logger.getLogger(UpdateMain.class);
        Util.intializeLogger(logger,"GreyAcquisitions");
        
        if(!initiatePropertiesFile())
            return;
        
        Globals.DIRECTORY_PATH =properties.getProperty("HOME_DIRECTORY");
        Util.setGlobals();
        logger.debug("GreyAcquisitions's Input Folder : " + Globals.WATCHED_DIRECTORY);
        Globals.IS_OSWIN = Util.getOSType();
        File file = new File(Globals.WATCHED_DIRECTORY);

        RoundRobin <Air> AirList= new RoundRobin <Air>();
        Globals.suitableSCs = new ArrayList<String>();
        initializeSCs(AirList);
        while(true){
            initializeAirs(AirList);
            try{
                Util.checkLogCapacity(logger,"GreyAcquisitions");
            } catch(Exception ex){
                logger.error("Error in checking for log file size");
            }
            File[] directoryListing = file.listFiles();
            if(directoryListing != null && directoryListing.length > 0){
                logger.debug("Number of files in the Watched folder is : "+ directoryListing.length);
                for(int i=0; i< directoryListing.length;i++){
                    try {
                        Util.archiveWatchDoLogFile(logger,"GreyAcquisitions");
                    } catch(Exception ex){
                        logger.error("Error in accumulating Watch Dog logs : " + ex);
                    }
                    try {
                        Util.accumulateLogs();
                    }catch(Exception ex){
                        logger.error("Error in accumulating logs : " + ex);
                    }
                    try{
                        logger.debug("File being processed : "+ directoryListing[i].getName());
                        HandleFile(directoryListing[i],AirList);
                        logger.debug("Done Processing file : "+ directoryListing[i].getName());
                    } catch(Exception ex){
                        logger.error("Error in Handling file : "+ directoryListing[i].getAbsolutePath());
                    }
                }
                
            }
            for(int i=0;i<AirList.size();i++)
                AirList.next().shutDown();
        }
        
     
    }
    public static boolean initiatePropertiesFile(){
        properties = new Properties();
        properties.clear();
        try {
            File file = new File(System.getProperty("user.dir")+"/Resources/config.properties");
            logger.debug("Resource File Path : "+ file.getAbsolutePath());
            FileInputStream fileInput = new FileInputStream(file);
            properties.load(fileInput);
            fileInput.close();
            logger.setLevel(Util.getLogLevel(properties.getProperty("LogLevel")));
            return true;
        } catch (FileNotFoundException ex) {
            logger.error("Config File Not Found : " + ex.getMessage());
            
        } catch (IOException ex) {
            logger.error("Config File Parsing Error: " + ex.getMessage());
        }
        return false;
    }
    
    public static void initializeAirs(RoundRobin <Air> AirList){
        for(Entry<Object, Object> e : properties.entrySet()) {
            if(((String) e.getKey()).startsWith("AIR_")){
                String value = (String) e.getValue();
                
                AirList.enqueue(new Air(value.split(",")[0],Integer.parseInt(value.split(",")[1]),value.split(",")[3]));
            }
        }
        
    }
    
    public static void initializeSCs(RoundRobin <Air> AirList){
        for(Entry<Object, Object> e : properties.entrySet()) {
            if(((String) e.getKey()).startsWith("SC_")){
                String value = (String) e.getValue();
                Globals.suitableSCs.add(value);
            }
        }
        
    }
    
    public static void HandleFile(File file,RoundRobin <Air> AirList){
        
        if(!file.exists())
        {
                System.out.println("Input file not found."); 
                System.exit(0);
        }
        
        BufferedReader reader = null;
        
        int numberOfThreads = Integer.parseInt(properties.getProperty("NumberOfThreads"));
        
        int counter =0;
        
        int linesPerThreadNumber = Util.getLinesNumberInAFile(file)/numberOfThreads;
        
        try {
            reader = new BufferedReader(new FileReader(file));
            String text = null;
            ArrayList<String> linesPerThread = new ArrayList<String>();
            while ((text = reader.readLine()) != null) {
                
                text=text.trim();
                String [] parts=text.split(",");
                
                if(parts.length!=2)
                {
                    //System.out.println(parts.length);
                    continue;
                }
                
                //Split input file 
                String msisdn= parts[0];
                String offerId = parts[1];
                
                if(linesPerThreadNumber > 0){
                    if(counter >= linesPerThreadNumber){
                        AirList.next().addRequest(linesPerThread,properties);
                        linesPerThread = new ArrayList<String>();
                        counter = 0;
                    }
                    linesPerThread.add(text);
                    counter ++;
                } else {
                    linesPerThread = new ArrayList<String>();
                    linesPerThread.add(text);
                    counter ++;
                    AirList.next().addRequest(linesPerThread,properties);
                }
            }
           
            
            
            
        } catch (Exception e) {
            e.getMessage();
            e.printStackTrace();
            
        }
        try {
            logger.debug("File : "+ file.getAbsolutePath() + " is getting archived");
            archiveFile(file);
           logger.debug("File : "+ file.getAbsolutePath() + " has been archived");
        }catch (Exception e) {
            logger.error("Error in archiving file : " + file.getAbsolutePath());
            logger.error(e);
        }
        
    }

    public  static void archiveFile(File currentFile){
        File newFile = null;
        
        SimpleDateFormat  sdf1 = new SimpleDateFormat("yyyyMMdd");
        try {
            if(Globals.IS_OSWIN){
                File file = new File (Globals.ARCHIVE_DIRECTORY+sdf1.format(new Date()));
                if (!file.exists()) {
                    if (file.mkdir()) {
                            logger.debug("Directory "+file.getAbsolutePath()+" is created!");
                            newFile = new File(file.getAbsolutePath()+"\\"+currentFile.getName());
                    } else {
                            logger.error("Failed to create directory "+ file.getAbsolutePath());
                    }
                }
                else {
                    logger.debug("Directory "+file.getAbsolutePath()+" already exists");
                    newFile = new File(file.getAbsolutePath()+"\\"+currentFile.getName());
                }
            }
            else {
                File file = new File (Globals.ARCHIVE_DIRECTORY+sdf1.format(new Date()));
                if (!file.exists()) {
                    if (file.mkdir()) {
                            logger.debug("Directory "+file.getAbsolutePath()+" is created!");
                            
                    } else {
                            logger.debug("Failed to create directory "+ file.getAbsolutePath());
                    }
                    
                }
                else {
                    logger.debug("Directory "+file.getAbsolutePath()+" already exists");
                    
                }
                newFile = new File(file.getAbsolutePath()+"/"+currentFile.getName());
            }
        }catch(Exception e){
            logger.error("Exception in creating the archive date folder or setting the path of the file in the archive");
            logger.error(e);
        }
        try{
            Files.move(Paths.get(currentFile.getAbsolutePath()), Paths.get(newFile.getAbsolutePath()),StandardCopyOption.REPLACE_EXISTING);
            logger.debug("File : " + currentFile.getAbsolutePath() + " is moved to the archive directory ");
//            newFile.delete();
//            if(currentFile.renameTo(newFile)){
//
//                logger.debug("File "+ currentFile.getAbsolutePath() + " is moved to " + newFile.getAbsolutePath());
//                logger.debug("work file "+newFile.getName()+" is archived");
//            }
//            else {
//                logger.error("Failed to move "+ currentFile.getAbsolutePath() + " to " + newFile.getAbsolutePath());
//                logger.debug("work file "+newFile.getName()+" was not archived");
//            }
        }catch(Exception e){
            logger.error("Error in moving the file : "+ currentFile.getName() + " To the archive folder");
            logger.error(e);
        }
     
    }
}