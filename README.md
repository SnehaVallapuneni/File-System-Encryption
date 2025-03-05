# File-System-Encryption

## Overview
This project implements a distributed file server using Java. The server provides functionalities for user management, file operations, and permissions handling. It also supports basic encryption for secure data transfer between replicas.

## Features
- **User Authentication**: Supports user login and registration.
- **File Operations**: Allows users to create, read, write, and delete files.
- **Permission Management**: Users can grant read, write, and ownership permissions to other users.
- **Data Replication**: Ensures data consistency across replicas using UDP sockets.
- **Encryption**: Implements DES encryption for secure file operations.

## File Structure
- `Server.java`: Main class containing the server logic.
- `meta/`: Contains metadata files for user data, permissions, and IP addresses.
  - `readpermissions.txt`
  - `writepermissions.txt`
  - `ownerpermissions.txt`
  - `users.txt`
  - `ipAddress.txt`
  - `ports.txt`
- `AllFiles/`: Stores all user-created files.
- `temp/`: Used for temporary decrypted files during read/write operations.

## Setup Instructions

### Prerequisites
- Java Development Kit (JDK) 8 or higher

### Running the Server
1. **Compile the code**:
   ```bash
   javac Server.java
   ```
2. **Start a server instance**:
   ```bash
   java Server <server_id>
   ```
   Replace `<server_id>` with the appropriate ID for each server.

3. **Metadata setup**:
   Ensure the following files are correctly set up in the `meta/` directory:
   - `ipAddress.txt`: Contains server IP addresses.
   - `ports.txt`: Lists the ports for sending/receiving data.

## Usage
Upon running, the server prompts for the following options:
1. **User login**
2. **User registration**

After login, users can:
- List available files
- Create a new file
- Write to a file
- Read a file
- Register a new user
- Grant permissions to another user
- Delete a file

## Encryption
The project uses **DES (Data Encryption Standard)** with a default key for encrypting file content and inter-server communication.

## Contributing
Contributions are welcome! Please ensure you test thoroughly before submitting pull requests.

## License
This project is licensed under the MIT License.

