
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@SuppressWarnings("unchecked")
public class Server{

   // static Map<String,List<String>> readPermissions=new HashMap<>(); 
   // static Map<String,List<String>> writePermissions=new HashMap<>(); 
    //static Map<String,List<String>> ownerPermissions=new HashMap<>();
    static List<Integer> replicaSendPortsList = new ArrayList<>();
    static List<Integer> replicaReceivePortsList = new ArrayList<>();
    static List<Integer> replicaPortsList = new ArrayList<>();
    static List<Integer> serverPortsList = new ArrayList<>();
    static List<String> filesAvailable=new ArrayList<>();
    //static Map<String,String> usersList=new HashMap<>();
    static Map<String,Integer> fileLocks = new HashMap<>();

    static Map<String,String> usersList;
    static Map<String,List<String>> readPermissions;
    static Map<String, List<String>> writePermissions;
    static Map<String,List<String>> ownerPermissions;

    static String loginUser;
    static DatagramSocket serverSocket,replicaSocket,replicaSendSocket,replicaReceiveSocket;
    static int id;
    static List<InetAddress> ips = new ArrayList<>();

    private static final String ALGORITHM = "DES";
    private static final byte[] DEFAULT_KEY_BYTES = "majbjdvi".getBytes(); 
    public static SecretKey getDefaultKey() {
        return new SecretKeySpec(DEFAULT_KEY_BYTES, ALGORITHM);
    }
    private static final SecretKey myKey=Server.getDefaultKey();

    public static void main(String args[]) throws FileNotFoundException,IOException, Exception{

        id = Integer.valueOf(args[0]);
        File ipAddressFile = new File("./meta/ipAddress.txt");
        Scanner scanner = new Scanner(ipAddressFile);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
    
            String[] parts = line.split(":");
            if (parts.length != 5) {
                System.err.println("Invalid format: " + line);
                continue;
            }
            String nodeId = parts[0];
            ips.add(InetAddress.getByName(nodeId));
        }

        File ports = new File("./meta/ports.txt");
        scanner = new Scanner(ports);
        while(scanner.hasNextLine()){
            String line = scanner.nextLine();
            replicaSendPortsList.add(Integer.valueOf(line.split(":")[1]));
            replicaReceivePortsList.add(Integer.valueOf(line.split(":")[2]));
            serverPortsList.add(Integer.valueOf(line.split(":")[3]));
            replicaPortsList.add(Integer.valueOf(line.split(":")[4]));
        }
        serverSocket = new DatagramSocket(serverPortsList.get(id));
        replicaSendSocket = new DatagramSocket(replicaSendPortsList.get(id));
        replicaReceiveSocket = new DatagramSocket(replicaReceivePortsList.get(id));
        replicaSocket = new DatagramSocket(replicaPortsList.get(id));


        for (int i = 0; i < ips.size(); i++) {
            System.out.println("Server " + i + " IP: " + ips.get(i) + " Port: " + replicaReceivePortsList.get(i));
        }

        //firstTimeInitializeMetaData();
        
        InitializeMetaData();

        Scanner sc=new Scanner(System.in);
        System.out.println("1. UserLogin \n 2. Register user");
        try {
            int choice=sc.nextInt();
            if(choice==1){
                System.out.println("Enter username ");
                String uname=sc.next();
                System.out.println("Enter password");
                String password=sc.next();
                String message="login "+uname+" "+password;
                server(message);
            }
            else if(choice==2){
                System.out.println("Enter username ");
                String uname=sc.next();
                System.out.println("Enter password");
                String password=sc.next();
                String message="register "+uname+" "+password;
                server(message);
            }
        } catch (Exception e) {
            System.out.println("Input mismatch");
        }
        
        Thread t1 = new Thread(new Runnable(){public void run(){
            try {replica();} catch (IOException ex) {}
        }});
        t1.start();
        starter();
    }
    public static void InitializeMetaData() {
        File readPermissionFile = new File("./meta/readpermissions.txt");
        readPermissions = (Map<String,List<String>>) deserializeObj(readPermissionFile.getPath());

        File writePermissionFile = new File("./meta/writepermissions.txt");
        writePermissions = (Map<String,List<String>>) deserializeObj(writePermissionFile.getPath());

        File ownerPermissionFile = new File("./meta/ownerpermissions.txt");
        ownerPermissions = (Map<String,List<String>>) deserializeObj(ownerPermissionFile.getPath());

        File usersFile = new File("./meta/users.txt");
        usersList = (Map<String,String>) deserializeObj(usersFile.getPath());
        String path = "./AllFiles";
        filesAvailable= listAllFiles(path);
        for(int i=0;i<filesAvailable.size();i++){
            fileLocks.put(filesAvailable.get(i), 0);
        }
    }
    public static void firstTimeInitializeMetaData(){
            readPermissions = new HashMap();
            serializeObj(readPermissions, "./meta/readPermissions.txt");
            writePermissions = new HashMap();
            serializeObj(writePermissions, "./meta/writePermissions.txt");
            ownerPermissions = new HashMap();              
            serializeObj(ownerPermissions, "./meta/ownerPermissions.txt");
            usersList = new HashMap();                    
            serializeObj(usersList, "./meta/users.txt");
    }
    public static List<String> listAllFiles(String path){
        File dir = new File(path);
        List<String> files = new LinkedList<>();
        for(int i=0;i<dir.list().length;i++)
        {
            File f = dir.listFiles()[i];
            if(f.isDirectory()){
                listAllFiles(path+"/"+f.getName());
            }
            else{
                files.add(path+"/"+f.getName());
            }
        }
        return files;
    }
    public static void serializeObj(Object obj,String fileName){
        try {
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                ObjectOutputStream oos=new ObjectOutputStream(fos);
                oos.writeObject(obj);
                oos.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
   @SuppressWarnings("unchecked")
    public static <T> T deserializeObj(String filename) {
        T object = null;
        try (FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis)) {
            object = (T) ois.readObject();
        } catch (Exception e) {
             e.printStackTrace();
        }
    return object;
    }

    public static void starter() throws Exception{
        while (true) { 
            System.out.println("1.List files\n2.Create File\n3.WriteFile\n4.Read File\n5.Register User\n6.Grant Permission\n7.DeleteFiles");
            Scanner sc=new Scanner(System.in);
            int choice=sc.nextInt();
            String msg;
            switch(choice){
                case 1: msg="list "+loginUser+" totalfiles";
                        server(msg);
                        break;
                case 2: 
                       System.out.println("Enter file name");
                       String fileName=sc.next();
                       msg="create "+loginUser+" "+fileName;
                       server(msg);
                       break;
                case 3: System.out.println("Enter file Name");
                        fileName=sc.next();
                        msg="write "+loginUser+" "+fileName;
                        server(msg);
                        break;
                case 4: System.out.println("Enter file Name");
                        fileName=sc.next();
                        msg="read "+loginUser+" "+fileName;
                        server(msg);
                        break;
                case 5: System.out.println("Enter the user Name");
                        String newUser = sc.next();
                        System.out.println("Enter the password");
                        String password = sc.next();
                        msg = "register "+newUser+" "+password;
                        server(msg);
                        break;
                case 6: System.out.println("Enter file Name");
                        fileName=sc.next();
                        System.out.println("Enter the new  user Name");
                        newUser = sc.next();
                        System.out.println("Enter the permission : owner-read-write enter 1 to grant permission");
                        String permission = sc.next();                        
                        msg="grantPermissions "+loginUser+" "+fileName+" "+newUser+" "+permission;
                        server(msg);
                        break;
                case 7: System.out.println("Enter file Name");
                        fileName=sc.next();
                        msg="delete "+loginUser+" "+fileName;
                        server(msg);
                        break;
                default:System.out.println("error enter valid number");
            }
        }
    }
    private static void server(String data) throws Exception {
        try {
            String op=data.split(" ")[0];
            String user=data.split(" ")[1];
                     
            String filename = "./AllFiles/" + data.split(" ")[2].trim();
            String message="";
            switch(op){
                case "create":
                    try {
                        filesAvailable = listAllFiles("./AllFiles");
                        File file=new File(filename);
                        if(!file.exists()){
                            file.createNewFile();
                            fileLocks.put(filename, 0);
                            filesAvailable.add(filename);

                            ownerPermissions.putIfAbsent(filename, new ArrayList<>());
                            readPermissions.putIfAbsent(filename, new ArrayList<>());
                            writePermissions.putIfAbsent(filename, new ArrayList<>());

                            ownerPermissions.get(filename).add(user);
                            readPermissions.get(filename).add(user);
                            writePermissions.get(filename).add(user);

                           /* List<String> userList=new ArrayList<>();
                            userList.add(loginUser);
                            ownerPermissions.put(filename,userList);
                            readPermissions.put(filename,userList);
                            writePermissions.put(filename,userList);*/
                            
                            serializeObj(ownerPermissions, "./meta/ownerpermissions.txt");
                            serializeObj(readPermissions, "./meta/readpermissions.txt");
                            serializeObj(writePermissions, "./meta/writepermissions.txt");
                            
                            for(int i=0;i<replicaReceivePortsList.size();i++){
                                if(id == i)
                                    continue;
                                byte[] replicaBufferedData = encrypt(data).getBytes();
                                DatagramPacket ReplicaDatapSend =new DatagramPacket(replicaBufferedData, replicaBufferedData.length, ips.get(i), replicaReceivePortsList.get(i));
                                System.out.println("Write permissions for " + filename + ": " + writePermissions.get(filename)+ ReplicaDatapSend.getPort());
                                serverSocket.send(ReplicaDatapSend); 
                            }
                            
                            System.out.println("File created");
                        }
                        else{
                            System.out.println("File exists");
                        }
                        filesAvailable = listAllFiles("./AllFiles");
                        //System.out.println("Updated filesAvailable: " + filesAvailable);
                    } catch (Exception e) {
                        e.printStackTrace();
                       // System.out.println("Error");
                    }
                    break;
                case "read":
                    try {
                        //filename = "./AllFiles/" + data.split(" ")[2].trim();
                        //System.out.println("Attempting to read file at path: " + filename);
                        //System.out.println("File exists on disk: " + new File(filename).exists());
                        //System.out.println("Current files in memory: " + filesAvailable);

                        if (filesAvailable.contains(filename) && new File(filename).exists()) {
                            List<String> readPerms = readPermissions.getOrDefault(filename, new ArrayList<>());
                            if (readPerms.contains(user)) {
                                File file = new File(filename);
                                boolean hasContent = false;
                                if(file.exists()){
                                    String filePath=readFile(filename);
                                    File tempFile=new File(filePath);
                                    Scanner readData=new Scanner(tempFile);
                                    while(readData.hasNextLine()){
                                        System.out.println(readData.nextLine());
                                        hasContent = true;
                                    }
                                    if (!hasContent) {
                                        System.out.println("File is empty!!!");
                                    }
                                    readData.close();
                                    tempFile.delete();
                                }
                                filesAvailable = listAllFiles("./AllFiles");
                                //System.out.println("Updated filesAvailable: " + filesAvailable);
                            } else {
                                System.out.println("You don't have permission to read this file.");
                            }
                        } else {
                            System.out.println("File does not exist.");
                        }
                    } catch (Exception e) {
                        //e.printStackTrace();
                        System.out.println("Error");
                    }
                    break;

                case "write":
                    try{
                        //System.out.println("Attempting to read file at path: " + filename);
                        //System.out.println("File exists on disk: " + new File(filename).exists());
                        //System.out.println("Current files in memory: " + filesAvailable);

                        if(filesAvailable.contains(filename) && new File(filename).exists()){
                            List<String> writePerms = writePermissions.getOrDefault(filename, new ArrayList<>());
                            if (writePerms.contains(user)) {
                             int lockStatus = fileLocks.getOrDefault(filename, 0);
                             if (lockStatus == 0) {
                                fileLocks.put(filename, 1); // Lock the file
                                File file = new File(filename);
                                System.out.println("Writable: " + file.canWrite());
                                if(writePermissions.get(filename).contains(user)){
                                    byte[] replicaBufferedData;
                                    DatagramPacket ReplicaDatapSend;
                                    for(int i=0;i<replicaPortsList.size();i++){
                                        if(id==i)
                                            continue;
                                        replicaBufferedData=encrypt(data).getBytes();
                                        ReplicaDatapSend=new DatagramPacket(replicaBufferedData,replicaBufferedData.length,ips.get(i),replicaReceivePortsList.get(i));
                                        serverSocket.send(ReplicaDatapSend);
                                    }
                                    System.out.println("enter data to write and enter `Done` after completing writing");
                                    while (true) { 
                                        Scanner sc=new Scanner(System.in);
                                        String dataToWrite=sc.nextLine();
                                        if(dataToWrite.startsWith("Done")){
                                            fileLocks.put(filename,0);
                                            for(int i=0;i<replicaReceivePortsList.size();i++){
                                                if(id==i)
                                                    continue;
                                                replicaBufferedData=encrypt("Done").getBytes();
                                                ReplicaDatapSend=new DatagramPacket(replicaBufferedData,replicaBufferedData.length,ips.get(i),replicaReceivePortsList.get(i));
                                                serverSocket.send(ReplicaDatapSend);
                                            }
                                                break;
                                        }
                                        writeFile(filename,dataToWrite);
                                        for(int i=0;i<replicaReceivePortsList.size();i++){
                                            if(id == i)
                                                continue;
                                            replicaBufferedData = encrypt(dataToWrite).getBytes();
                                            ReplicaDatapSend =new DatagramPacket(replicaBufferedData, replicaBufferedData.length, ips.get(i), replicaPortsList.get(i));
                                            serverSocket.send(ReplicaDatapSend); 
                                            }
                                        }
                                        System.out.println("Data written to the file");
                                        fileLocks.put(filename,0);
                                        filesAvailable = listAllFiles("./AllFiles");
                                    }
                                    else{
                                        System.out.println("Error finding file");
                                    }
                                }
                                else{
                                    System.out.println("cannot write to the file");
                                }
                            }
                        }
                        else{
                            System.out.println("File does not exist");
                        }
                    } 
                    catch (Exception e) {
                        //e.printStackTrace();
                       System.out.println("Error");
                    }
                    break;
                case "login":
                    try {
                        String password=data.split(" ")[2];
                        if(usersList.containsKey(user)&&usersList.get(user).equals(password)){
                            loginUser=user;
                            System.out.println("Login success");
                        }
                        else{
                            System.out.println("User does not exist. Exiting");
                            System.exit(1);
                        }
                    } catch (Exception e) {
                        System.out.println("Error");
                    }
                    break;
                case "register":
                    try {
                        String password=data.split(" ")[2];
                        if(!usersList.containsKey(user)){
                            usersList.put(user,password);
                            
                            for(int i=0;i<replicaReceivePortsList.size();i++){
                                if(id == i)
                                    continue;
                                System.out.println(replicaPortsList.get(i));
                                //byte[] replicaBufferedData = encrypt(data).getBytes();
                                byte[] replicaBufferedData = encrypt(data).getBytes();
                                DatagramPacket ReplicaDatapSend =new DatagramPacket(replicaBufferedData, replicaBufferedData.length, ips.get(i), replicaPortsList.get(i));
                                //System.out.println(ReplicaDatapSend.getPort()+" "+ReplicaDatapSend.getAddress());
                                serverSocket.send(ReplicaDatapSend); 
                            }
                            serializeObj(usersList, "./meta/users.txt");
                            System.out.println("User Added");
                        }
                        else{
                            System.out.println("User exists");
                        }
                    } catch (Exception e) {
                        //e.printStackTrace();
                        System.out.println("Error");
                    }
                    break;

                case "list":
                    try{
                        for(String s:filesAvailable){
                            if (new File(s).isDirectory() ||(ownerPermissions.get(s) != null && ownerPermissions.get(s).contains(user)) ||
                                (readPermissions.get(s) != null && readPermissions.get(s).contains(user)) ||
                                (writePermissions.get(s) != null && writePermissions.get(s).contains(user))) {
                                    System.out.println(s.substring(11));
                            }
                        }
                    }
                    catch (Exception e) {
                        //e.printStackTrace();
                        System.out.println("No such files exist");
                    }
                    break;

                case "grantPermissions":
                    try{
                        List<String> ownerPerms = ownerPermissions.get(filename);
                        if (ownerPerms != null && ownerPerms.contains(user)) {
                            String uname=data.split(" ")[3];
                            String perm=data.split(" ")[4];
                            if(perm.charAt(0)=='1'){
                                ownerPermissions.get(filename).add(uname);
                                readPermissions.get(filename).add(uname);
                                writePermissions.get(filename).add(uname);
                            }
                            if(perm.charAt(1)=='1'){
                                writePermissions.get(filename).add(uname);
                               // serializeObj(writePermissions, "./meta/writepermissions.txt");
                            }
                            if(perm.charAt(2)=='1'){
                                readPermissions.get(filename).add(uname);
                               // serializeObj(readPermissions, "./meta/readpermissions.txt");
                            }
                            serializeObj(readPermissions,"./meta/readpermissions.txt");
                            serializeObj(writePermissions,"./meta/writepermissions.txt");
                            serializeObj(ownerPermissions,"./meta/ownerpermissions.txt");
                            for(int i=0;i<replicaReceivePortsList.size();i++){
                                if(id == i)
                                    continue;
                                byte[] replicaBufferedData = encrypt(message).getBytes();
                                DatagramPacket ReplicaDatapSend =new DatagramPacket(replicaBufferedData, replicaBufferedData.length, ips.get(i), replicaPortsList.get(i));
                                serverSocket.send(ReplicaDatapSend); 
                            }
                            System.out.println("Permissions granted to the user");
                        }
                        else{
                            System.out.println("You cannot grant permissions");
                        }
                    }
                    catch (Exception e) {
                        System.out.println("File does not exist or you cannot grant permissions");
                    }
                    break;

                case "delete": 
                    try {
                        File file = new File(filename);
                        if (file.exists() && ownerPermissions.containsKey(filename) && ownerPermissions.get(filename).contains(user)) {
                            file.delete();
                            
                            for (int i = 0; i < replicaReceivePortsList.size(); i++) {
                                if (id == i) continue;
                                byte[] replicaBufferedData = encrypt(data).getBytes();
                                DatagramPacket ReplicaDatapSend = new DatagramPacket(replicaBufferedData,replicaBufferedData.length,ips.get(i),replicaReceivePortsList.get(i));
                                System.out.println("Sending delete request to replica at: " + ips.get(i) + ":" + replicaReceivePortsList.get(i));
                                serverSocket.send(ReplicaDatapSend);
                            }
                            filesAvailable.remove(filename);
                            fileLocks.remove(filename);
                            readPermissions.remove(filename);
                            writePermissions.remove(filename);
                            ownerPermissions.remove(filename);

                            serializeObj(readPermissions, "./meta/readpermissions.txt");
                            serializeObj(writePermissions, "./meta/writepermissions.txt");
                            serializeObj(ownerPermissions, "./meta/ownerpermissions.txt");

                            filesAvailable = listAllFiles("./AllFiles");

                            System.out.println("File deleted and replicated.");
                        } else {
                            System.out.println("File does not exist or you don't have permission to delete it.");
                        }
                    } catch (Exception e) {
                       e.printStackTrace();
                    }
                    break;

            }
        }
         catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void replica() throws IOException{
        try {
            
            byte[] receive=new byte[65535];
            DatagramPacket receivedPacket=null;
            while (true){
                receivedPacket = new DatagramPacket(receive, receive.length);
                replicaReceiveSocket.receive(receivedPacket);
                String receivedData = decrypt(data(receive).toString());
                System.out.println("Replica " + id + " is listening on port: " + replicaReceiveSocket.getLocalPort());
                System.out.println("Replica received raw data: " + receivedData);
                String operation = receivedData.split(" ")[0];
                String user = receivedData.split(" ")[1];
            
                String fileName;
                if(receivedData.split(" ")[2].startsWith("./AllFiles"))
                    fileName =receivedData.split(" ")[2];
                else
                    fileName = "./AllFiles/" + receivedData.split(" ")[2];
                int port = receivedPacket.getPort();
                InetAddress ipaddress = receivedPacket.getAddress();
                switch(operation){
                    case "read":
                            try{
                                File file = new File(fileName);
                                if(file.exists()){
                                        String tempPath = readFile(fileName);
                                        File tempFile = new File(tempPath);
                                        Scanner readData = new Scanner(tempFile);
                                        while(readData.hasNextLine()){
                                            String data = encrypt(readData.nextLine());
                                            byte[] buf = data.getBytes();
                                            DatagramPacket DpSend =new DatagramPacket(buf,buf.length,ipaddress,port);
                                            replicaReceiveSocket.send(DpSend);   
                                        }
                                        readData.close();
                                        tempFile.delete();
                                        String msg = encrypt("done"+user);
                                        byte[] buf = msg.getBytes();   
                                        DatagramPacket DpSend = new DatagramPacket(buf, buf.length, ipaddress, port);
                                        replicaReceiveSocket.send(DpSend); 
                                }
                            }
                            catch(Exception e){
                                String msg = encrypt("Error"+user);
                                byte[] buf = msg.getBytes();   
                                DatagramPacket DpSend =new DatagramPacket(buf, buf.length, ipaddress, port);
                                replicaReceiveSocket.send(DpSend); 
                            }
                            break;
                    case "write":
                        try {
                            fileLocks.put(fileName,1);
                            File file = new File(fileName);
                            if(file.exists()){
                                byte[] receiveData = new byte[65535];
                                DatagramPacket PacketReceive = null;
                                while (true)
                                {
                                    PacketReceive = new DatagramPacket(receiveData, receiveData.length);
                                    replicaSocket.receive(PacketReceive);
                                    //System.out.println("Client:-" + decrypt(data(receiveData).toString()));
                                    String writeData = decrypt(data(receiveData).toString());
                                    if(writeData.startsWith("Done")){
                                        fileLocks.put(fileName,0);
                                        break;
                                    }
                                    writeFile(fileName,writeData);
                                    receiveData = new byte[65535];
                                }
                            }
                        } 
                        catch (Exception e) {
                        }
                        break;
                    case "create":
                        File file = new File(fileName);
                        if(!file.exists()){
                            file.createNewFile();
                            fileLocks.put(fileName, 0);
                            filesAvailable.add(fileName);

                            ownerPermissions.putIfAbsent(fileName, new ArrayList<>());
                            readPermissions.putIfAbsent(fileName, new ArrayList<>());
                            writePermissions.putIfAbsent(fileName, new ArrayList<>());

                            ownerPermissions.get(fileName).add(user);
                            readPermissions.get(fileName).add(user);
                            writePermissions.get(fileName).add(user);

                            serializeObj(readPermissions, "./meta/readpermissions.txt");
                            serializeObj(writePermissions, "./meta/writepermissions.txt");
                            serializeObj(ownerPermissions, "./meta/ownerpermissions.txt");
                        }
                        break;
                    case "delete":
                        //System.out.println("Received delete request on replica for: " + fileName);
                        file = new File(fileName);
                        if(file.exists()){
                            file.delete();
                            fileLocks.remove(fileName);

                            readPermissions.remove(fileName);
                            writePermissions.remove(fileName);
                            ownerPermissions.remove(fileName);
                            
                            filesAvailable.remove(fileName);
                            serializeObj(readPermissions, "./meta/readpermissions.txt");
                            serializeObj(writePermissions, "./meta/writepermissions.txt");
                            serializeObj(ownerPermissions, "./meta/ownerpermissions.txt");
                            
                        }
                        else{
                            System.out.println("File not found on replica");
                        }
                        break;
                    case "register":
                        String password = receivedData.split(" ")[2];
                        usersList.put(user, password);
                        serializeObj(usersList, "./meta/users.txt");
                        //usersList = (Map<String, String>) deserializeObj("./meta/users.txt");
                        System.out.println("User registered on replica: " + user);
                        break;
                    case "grantPermissions":
                        String uname = receivedData.split(" ")[3];
                        String perm = receivedData.split(" ")[4];
                        if(perm.charAt(0)=='1'){
                            ownerPermissions.get(fileName).add(uname);
                            readPermissions.get(fileName).add(uname);
                            writePermissions.get(fileName).add(uname);
                            }
                        if(perm.charAt(1)=='1'){
                            writePermissions.get(fileName).add(uname);
                           // serializeObj(writePermissions, "./meta/writepermissions.txt");
                        }
                        if(perm.charAt(2)=='1'){
                                readPermissions.get(fileName).add(uname);
                               // serializeObj(readPermissions, "./meta/readpermissions.txt");
                        }
                        serializeObj(readPermissions, "./meta/readpermissions.txt");
                        serializeObj(writePermissions, "./meta/writepermissions.txt");
                        serializeObj(ownerPermissions, "./meta/ownerpermissions.txt");
                        break;
                }
                receive = new byte[65535];
            } 
        }
        catch (Exception e) {
        }
    }
    public static StringBuilder data(byte[] data)
    {
        if (data == null)
            return null;
        StringBuilder str = new StringBuilder();
        int i = 0;
        while (data[i] != 0){
            str.append((char)data[i]);
            i++;
        }
        return str;
    }
    public static String readFile(String filename){
        String temp="./temp/decrypt";
        File tempFile=new File(temp);
        try {
            if(tempFile.exists()){
                tempFile.delete();
                tempFile.createNewFile();
            }
            else{
                tempFile.createNewFile();
            }
            File file=new File(filename);
            decrypt(file,tempFile);
            return temp;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public static void writeFile(String filename,String writeData){
        try {
            String temp="./temp/decrypt";
            File tempFile=new File(temp);
            if(tempFile.exists()){
                tempFile.delete();
                tempFile.createNewFile();
            }
            else{
                tempFile.createNewFile();
            }
            File file=new File(filename);
            decrypt(file,tempFile);
            BufferedWriter bw=new BufferedWriter(new FileWriter(temp,true));
            bw.write(writeData);
            bw.close();
            encrypt(tempFile,file);
            tempFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
           // System.out.println("Error");
        }
    }
    private static void decrypt(File inpfile, File outFile) {
        crypto(Cipher.DECRYPT_MODE,myKey,inpfile,outFile);
    }
    private static void encrypt(File inpFile,File outFile){
        crypto(Cipher.ENCRYPT_MODE,myKey,inpFile,outFile);
    }
    public static void crypto(int ciphermode,SecretKey mykey,File inpFile,File outFile){
        try {
            Cipher cipher=Cipher.getInstance(ALGORITHM);
            cipher.init(ciphermode, mykey);
            FileInputStream fis=new FileInputStream(inpFile);
            byte[] inpbytes=new byte[(int)inpFile.length()];
            fis.read(inpbytes);
            byte[] outbytes=cipher.doFinal(inpbytes);
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(outbytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static String encrypt(String string) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,myKey);
            byte[] encryptedData = cipher.doFinal(string.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            System.out.println("Error while encrypting: " + e.toString());
        }
        return null;
    }
    public static String decrypt(String string){
        try {
            Cipher cipher=Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,myKey);
            byte[] decData=Base64.getDecoder().decode(string);
            return new String(cipher.doFinal(decData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("Error");
        }
        return null;
    }
}