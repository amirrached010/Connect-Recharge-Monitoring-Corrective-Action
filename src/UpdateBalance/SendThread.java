/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package UpdateBalance;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.Random;
import javax.swing.text.Document;
import javax.xml.parsers.*;
import javax.xml.xpath.XPath;
import javax.xml.xpath.*;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.xml.sax.*;

/**
 *
 * @author Omar.AlFar
 */
public class SendThread implements Runnable {

    // String request;
    String serverUrl;
    ArrayList<String> inputList;
    String password;
    String offerId;
    String msisdn;
    Logger logger;
    String appenderName;
    Properties properties;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'hh:mm:ss+0200");
    public SendThread(String url, ArrayList<String> inputList, String password,
            Properties properties) {
        // this.request = request;
        this.serverUrl = url;
        this.inputList = inputList;
        this.password = password;
        this.properties = properties;
        
        intializeLogger();
    }

    public void run() {
        
        for(int i=0; i<inputList.size(); i++){
            this.msisdn = inputList.get(i).split(",")[0];
            this.offerId = inputList.get(i).split(",")[1];
            String response = "";
        
            String checkServiceClassRequest = formatRequestV1(Globals.UCIPRequest.GetAccountDetails);


            response = sendRequest(checkServiceClassRequest);


            String sc = parseSC(response);
            logger.debug("The dial : "+ msisdn + " has SC : "+ sc);
            if(Globals.suitableSCs.contains(sc)){
                String request = formatRequestV1(Globals.UCIPRequest.UpdateOffer);


                response = sendRequest(request);

                String status = parseResponse(response);

                if(status.equals("0")){
                    sendSMS(msisdn+","+properties.getProperty("SMS_Script")+",2\n", 1);
                    logger.debug("For msisdn : "+msisdn +" the offer : "+offerId+" was added successfully");
                    String addPAMRequest = formatRequestV1(Globals.UCIPRequest.AddPam);
                    response = sendRequest(addPAMRequest);
                    status = parseResponse(response);
                    if(status.equals("0"))
                        logger.debug("PAM Added successfully for the dial : "+msisdn);
                    else
                    if(status.equals("190")){
                        logger.debug("PAM already exists for the dial : "+ msisdn);
                        String reset5Accumulators = formatRequestV1(Globals.UCIPRequest.ResetFiveAccumulator);
                        logger.info(reset5Accumulators);
                        response = sendRequest(reset5Accumulators);
                        status = parseResponse(response);
                        if(status.equals("0")){
                            logger.debug("Accumulators resetted successfully for the dial : "+msisdn);

                        }else {
                            logger.debug("Error in Resetting Accumulators for the dial : "+msisdn + " Error Code : "+ status);
                        }
                    } else {
                        logger.error("Error in adding PAM for the dial : "+ msisdn + " Error Code : "+ status);
                    }



                }else {
                    logger.debug("The dial : "+ msisdn + " has a responseCode for adding Offer : "+status );
                }


            }
            else{
                logger.error("Service Class : "+sc+" not configured");
            }
        }
        
        
        stopThread();
    }

    public String sendRequest(String request) {

        OutputStreamWriter streamWriter = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        String response = "";
        //tring password = "Z3NkYzpnc2Rj";
        // String password = "YWlydXNlcjpsYXVuY2gwNQ==";

        try {
            //   System.out.println("in 1");
            URLConnection urlConnection = getConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setUseCaches(false);
            urlConnection.setRequestProperty("Method", "POST");
            urlConnection.setRequestProperty("User-Agent", "UGw Server/5.0/1.0");
            urlConnection.setRequestProperty("Content-Type", "text/xml");
            urlConnection.setRequestProperty("Authorization", "Basic " + password);
            urlConnection.setRequestProperty("Host", "Air");
            String currentRequest = request.replace("$msisdn", msisdn);
            urlConnection.setRequestProperty("Content-Length", "" + currentRequest.length());
            
            streamWriter = new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8");
            
            streamWriter.write(currentRequest);
            streamWriter.flush();
            
            inputStreamReader = new InputStreamReader(urlConnection.getInputStream());
            bufferedReader = new BufferedReader(inputStreamReader);
            String responseLine = "";
            while ((responseLine = bufferedReader.readLine()) != null) {
                response = response + responseLine;
            }
            
            // System.out.println(response);
        } catch (Exception exception) {
            logger.error("Exception in sendRequest ");
            logger.error(exception);
        } finally {
            if (streamWriter != null) {
                try {
                    streamWriter.close();
                } catch (IOException ioException) {
                    logger.error("Exception in sendRequest ");
                    logger.error(ioException);
                }
            }
            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                } catch (IOException ioException) {
                    logger.error("Exception in sendRequest ");
                    logger.error(ioException);
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ioException) {
                    logger.error("Exception in sendRequest ");
                    logger.error(ioException);
                }
            }
        }
        //   System.out.println("in 5");
        return response;
    }

    private URLConnection getConnection() throws IOException {

        URL url = new URL(serverUrl);

        URLConnection urlConnection = url.openConnection();

        return urlConnection;
    }

    private String formatRequest() {
        Random randomGenerator = new Random();
        String id = randomGenerator.nextInt(10000000) + "";

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'hh:mm:ss+2000");

        String date = dateFormat.format(new Date()).toString();
        //String date = "20130910T00:00:00+2000";



        String xml = "<?xml version=\"1.0\"?><methodCall><methodName>UpdateBalanceAndDate</methodName><params><param><value><struct>";

        //THIS VALUE EXT TO BE REPLACE IF NEEDED 
        //THE ORIGIN NODE NAME
        xml += "<member><name>originNodeType</name><value><string>EXT</string></value></member>";

        xml += "<member><name>originOperatorID</name><value><string>Etisalat</string></value></member>";

        xml += "<member><name>originHostName</name><value><string>Air</string></value></member>";

        xml += "<member><name>subscriberNumberNAI</name><value><int>2</int></value></member>";

        xml += String.format("<member><name>originTransactionID</name><value><string>%s</string></value></member>", id);

        xml += String.format("<member><name>originTimeStamp</name><value><dateTime.iso8601>%s</dateTime.iso8601></value></member>", date);

        xml += String.format("<member><name>transactionCurrency</name><value><string>EGP</string></value></member>");

        xml += String.format("<member><name>externalData1</name><value><string>CADEINSr</string></value></member>");

        xml += String.format("<member><name>externalData2</name><value><string>CADEINSr</string></value></member>");

        xml += String.format("<member><name>adjustmentAmountRelative</name><value><string>%s</string></value></member>", offerId);


        if (msisdn.startsWith("20")) {
            msisdn = msisdn.replaceFirst("20", "");
        }

        xml += String.format("<member><name>subscriberNumber</name><value><string>%s</string></value></member>", msisdn);
        xml += "</struct></value></param></params></methodCall>";

        return xml;

    }

    private String formatRequestV1(Globals.UCIPRequest ucipRequest){
        String updateBalanceAndDateRequest ="";
        updateBalanceAndDateRequest = readFromFile(new File(System.getProperty("user.dir")+"/Requests/"+ucipRequest+".txt")).toString();
        switch(ucipRequest){
            case UpdateOffer: 
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$offerID", offerId);
                Calendar cal = Calendar.getInstance(); 
                cal.add(Calendar.MONTH, 6);
                java.util.Date dt = cal.getTime();
                String currentDate = sdf.format(new Date());
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$startDateTime", currentDate);
                currentDate = sdf.format(dt);
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$expiryDateTime", currentDate);
                break;
            case AddPam: 
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$PAMServiceID", properties.getProperty("PAMServiceID"));
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$PAMCLASSID", properties.getProperty("PAMClassID"));
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$PAMSCHEDULEID", properties.getProperty("PAMScheduleID"));
                break;
            case ResetFiveAccumulator:
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$AccumulatorID_1", properties.getProperty("AccumulatorID_1"));
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$AccumulatorID_2", properties.getProperty("AccumulatorID_2"));
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$AccumulatorID_3", properties.getProperty("AccumulatorID_3"));
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$AccumulatorID_4", properties.getProperty("AccumulatorID_4"));
                updateBalanceAndDateRequest = updateBalanceAndDateRequest.replace("$AccumulatorID_5", properties.getProperty("AccumulatorID_5"));
                break;
        };
        return updateBalanceAndDateRequest;        
    }
    
    private String parseResponse(String response){
        String status = "";
        if (response.length() > 0) {

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            InputSource source = new InputSource(new StringReader(
                    response));

            try {
                status = xpath.evaluate("/methodResponse/params/param/value/struct/member[name='responseCode']/value/i4", source);
                
            } catch (XPathExpressionException ex) {
               logger.error("Cannot Parse Response : " +ex);
            }

            xpath.reset();
        } else {
            status = "-1";
        }
        return status;
    }
    
    private String parseSC(String response){
        String status = "";
        if (response.length() > 0) {

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            InputSource source = new InputSource(new StringReader(
                    response));

            try {
                status = xpath.evaluate("/methodResponse/params/param/value/struct/member[name='serviceClassCurrent']/value/i4", source);
                
            } catch (XPathExpressionException ex) {
               logger.error("Cannot Parse Response : " +ex);
            }

            xpath.reset();
        } else {
            status = "-1";
        }
        return status;
    }
    
    public  StringBuilder readFromFile(File dialsFile){
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try{
            br = new BufferedReader(new FileReader(dialsFile));
        } catch(FileNotFoundException e){
            
        }
        
        try {
            
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            
            br.close();
        } catch (Exception ex) {
            logger.error("Exception in reading file : "+ dialsFile.getName());
            logger.error(ex);
        } 
        return sb;
    }
    
    public  void intializeLogger(){

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        RollingFileAppender appender = new RollingFileAppender();
        
        appender.setAppend(true);
        appender.setMaxFileSize("1MB");
        appender.setMaxBackupIndex(1);
        String fileName="logs/GreyAcquisitions_ThreadLog_"+dateFormat.format(new Date())+"_"+this.msisdn;
        appenderName = fileName;
        appender.setName(appenderName);
        logger = Logger.getLogger(fileName);    
        appender.setFile(fileName + ".log");
        PatternLayout layOut = new PatternLayout();
        layOut.setConversionPattern("%d{yyyy-MM-dd HH:mm:ss} %-5p :%L - %m%n");
        appender.setLayout(layOut);
        appender.activateOptions();
        logger.removeAllAppenders();
        logger.addAppender(appender);
        logger.debug("Log appended : " + fileName);
        
    }
    
    public  void sendSMS(String toString,int lineCounter) {
        Writer writer = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String currentFileName ="";
        File resultFile = null;
        File newFile  = null;
        if(Util.getOSType() == Globals.OS_UNIX){
            currentFileName = Globals.SMS_PREPARATION_DIRECTORY+"GreyAcquisitions_"+sdf.format(new Date())+"_L"+lineCounter+"_V"+this.inputList+".txt";
            resultFile = new File(currentFileName);
            newFile = new File(Globals.SMS_DIRECTORY+resultFile.getName());
                    
        }
        else{
            currentFileName = Globals.SMS_PREPARATION_DIRECTORY+"GreyAcquisitions_"+sdf.format(new Date())+"_L"+lineCounter+"_V"+this.inputList+".txt";
            resultFile = new File(currentFileName);
            newFile = new File(Globals.SMS_DIRECTORY+resultFile.getName());
        }
        
        
        if(!resultFile.exists())
            try {
                resultFile.createNewFile();
        } catch (IOException ex) {
           logger.error("Cannot create the SMS file in the SMS Preparation Directory: "+ currentFileName);
        }
        try {
            FileWriter fw = new FileWriter(resultFile,true);
            //BufferedWriter writer give better performance
            BufferedWriter bw = new BufferedWriter(fw);
            bw.append(toString);
            bw.close();
            // Send the file to the SMS tool
            //logger.info("Deleting moved file : "+ newFile.delete());
            try{
            Files.move(Paths.get(resultFile.getAbsolutePath()), Paths.get(newFile.getAbsolutePath()),StandardCopyOption.REPLACE_EXISTING);
            logger.info("File "+ newFile.getAbsolutePath()+" is moved to the SMS Directory");
            }catch(Exception e){
                logger.error("Failed to move File "+ resultFile.getAbsolutePath()+" to the SMS Directory : " + Globals.SMS_DIRECTORY + " under name : " + newFile.getName());
                logger.error("Exception : "+ e);
            }    
//            if(resultFile.renameTo(newFile)){
//              logger.info("File "+ newFile.getAbsolutePath()+" is moved to the SMS Directory");
//            }
//            else {
//               logger.error("Failed to move File "+ resultFile.getAbsolutePath()+" to the SMS Directory : " + Globals.SMS_DIRECTORY + " under name : " + newFile.getName());
//            }
        } catch (UnsupportedEncodingException ex) {
            logger.error("Error in writing in file : "+ currentFileName);
        } catch (FileNotFoundException ex) {
            logger.error("File not found : "+ currentFileName);
        } catch (IOException ex) {
            logger.error("IO Exception: "+ currentFileName);;
        }
    }
 
    public void stopThread(){
        logger.debug("Thread Stopped for dial : "+ inputList);
        logger.debug("------------------------------------------------------------------------------------------------");
        logger.getAppender(appenderName).close();
        //LogManager.shutdown();
    }
}
