# Weather Aggregation Server Application

## Table of Contents
1. [Project Overview](#project-overview)
2. [Prerequisites](#prerequisites)
3. [Setup and Installation](#setup-and-installation)
4. [Running the Application](#running-the-application)
5. [Running Automated Tests](#running-automated-tests)
6. [Manual Testing](#manual-testing)
7. [Design Choices and Decisions](#design-choices-and-decisions)
<br>

## Project Overview
The Weather Aggregation Server application is a Java-based client-server system that aggregates weather data from multiple sources and serves this data to clients using a RESTful API. The server can handle multiple ContentServers sending data and multiple GETClients requesting data. The server uses Lamport clocks to maintain a consistent order of events and supports synchronization for updates.

The core components include:
- **AggregationServer**: Collects and serves weather data.
- **ContentServer**: Sends weather data updates to the AggregationServer.
- **GETClient**: Retrieves weather data from the AggregationServer.
<br>

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
<br>

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
<br>

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
<br>

## Running Automated Tests
The project includes comprehensive unit and integration tests using JUnit. To run all the tests, use the following Maven command:
```bash
mvn test
```

### Run Individual Tests
To run a specific test class, use:
```bash
mvn -Dtest=<TestClassName> test
```
For example:
```bash
mvn -Dtest=AggregationServerTest test
```
<br>

## Manual Testing
To manually test the application, follow these steps:
<br>

### Step 1: Start the AggregationServer
Run the AggregationServer as described above.

### Step 2: Create Content Data Files or Use Existing Data Files
You can create your own weather data files in the `content/` directory (or your chosen directory), or you can use the provided sample data files located in the `content/` folder.

### Step 3: Start Multiple ContentServers
Run multiple ContentServer instances on different terminals to simulate concurrent data updates:
```bash
java -cp target/classes com.example.weather.ContentServer http://localhost:4567 content/station1.txt
java -cp target/classes com.example.weather.ContentServer http://localhost:4567 content/station2.txt
```

### Step 4: Start GETClient Instances
Run GETClient instances to verify data retrieval:
```bash
java -cp target/classes com.example.weather.GETClient http://localhost:4567 stationTest1
java -cp target/classes com.example.weather.GETClient http://localhost:4567 stationTest2
```

### Step 5: Test Data Expiration
Terminate the ContentServer instances, wait for 30 seconds and re-run the GETClient commands. You should see “No content available” after data expires.
<br>

## Design Choices and Decisions

### 1. PUT Request Interval and Retry Mechanism
The **Content Server** is designed to **send PUT requests every `x` seconds** to the Aggregation Server to provide periodic updates of the weather data. This ensures that the Aggregation Server is always receiving fresh data and helps simulate a real-time streaming update system.

- **Periodic Update Interval (`x`)**: 
  - A configurable time interval (e.g., 10 seconds) is used to define how frequently each Content Server sends a PUT request. This value was chosen to balance between keeping the Aggregation Server up-to-date.

- **Retry Mechanism (`y` Retries)**:
  - To handle transient network issues, each PUT request is configured to **retry `y` times** (e.g., 3 retries) if it encounters a failure. 
  - After `y` unsuccessful retries, the Content Server will log the failure and continue its periodic updates without retrying further until the next scheduled interval.

### 2. Synchronization Strategy
**Lamport Clocks** were chosen to ensure consistency across multiple ContentServers. This ensures events order without needing a central clock.

### 3. Expiration Mechanism for Weather Data
We implemented an **expiration mechanism** using timestamps to ensure that only the most recent 20 weather entries are kept. This was chosen over a fixed-size queue to allow for flexible data management.

### 4. Custom JSON Parsing 
Although libraries like Jackson could simplify JSON parsing, a custom JSON parser was implemented for finer control and to earn bonus points as per assignment requirements.
