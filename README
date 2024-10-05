# Weather Aggregation Server Application

## Table of Contents
1. [Project Overview](#project-overview)
2. [Prerequisites](#prerequisites)
3. [Setup and Installation](#setup-and-installation)
4. [Running the Application](#running-the-application)
5. [Running Automated Tests](#running-automated-tests)
6. [Manual Testing](#manual-testing)
7. [Known Issues](#known-issues)
8. [Future Enhancements](#future-enhancements)

## Project Overview
The Weather Aggregation Server application is a Java-based client-server system that aggregates weather data from multiple sources and serves this data to clients using a RESTful API. The server can handle multiple ContentServers sending data and multiple GETClients requesting data. The server uses Lamport clocks to maintain a consistent order of events and supports synchronization for updates.

The core components include:
- **AggregationServer**: Collects and serves weather data.
- **ContentServer**: Sends weather data updates to the AggregationServer.
- **GETClient**: Retrieves weather data from the AggregationServer.

## Prerequisites
Before running the application, ensure you have the following prerequisites installed:

- **Java Development Kit (JDK)**: Version 11 or higher.
- **Apache Maven**: Used for building and managing dependencies.
- **Git** (optional): To clone the repository.

### Checking Prerequisites
```bash
# Check Java version
java -version

# Check Maven version
mvn -version
```
## Setup and Installation
1. Clone the Repository (optional, if using version control):
```bash
git clone https://github.com/a1842806/weather-aggregation.git
cd weather-aggregation-server
```

2. Compile the Application Using Maven:
In the root directory of the project, run:
```bash
mvn clean compile
```

## Running the Application
1. Start the AggregationServer
To start the main server, run the following command from the project directory:

```bash
java -cp target/classes com.example.weather.AggregationServer
```

The server will start on the default port 4567. You can specify a different port using:

```bash
java -cp target/classes com.example.weather.AggregationServer \<port-number\>
```

2. Start the ContentServer(s)
ContentServer reads data from a file and sends it to the AggregationServer. To start a ContentServer, use:

```bash
java -cp target/classes com.example.weather.ContentServer \<server-url\> \<file-path\>
```

Example:
```bash
java -cp target/classes com.example.weather.ContentServer http://localhost:4567 content/original_data.txt
```

You can run multiple ContentServer instances concurrently with different data files.

3. Start the GETClient
GETClient retrieves weather data from the AggregationServer. To run GETClient, use:

```bash
java -cp target/classes com.example.weather.GETClient \<server-url\> \<station-id\>
```

Example:
```bash
java -cp target/classes com.example.weather.GETClient http://localhost:4567 station1
```

