## Quick Start

This section describes how to run YCSB with an akka based experimental key-value-store. 

### 1. Start the akka cluster

### 2. Install Java and Maven

### 3. Set Up YCSB

Git clone YCSB and compile:

    git clone http://github.com/brianfrankcooper/YCSB.git
    cd YCSB
    mvn -pl site.ycsb:redis-binding -am clean package

### 4. Provide Redis Connection Parameters
    
Set host, port, password, and cluster mode in the workload you plan to run. 

- `redis.host`
- `redis.port`
- `redis.password`
  * Don't set the password if redis auth is disabled.
- `redis.cluster`
  * Set the cluster parameter to `true` if redis cluster mode is enabled.
  * Default is `false`.

Or, you can set configs with the shell command, EG:

    ./bin/ycsb load redis -s -P workloads/workloada -p "redis.host=127.0.0.1" -p "redis.port=6379" > outputLoad.txt

### 5. Load data and run tests

Load the data:

    ./bin/ycsb load redis -s -P workloads/workloada > outputLoad.txt

Run the workload test:

    ./bin/ycsb run redis -s -P workloads/workloada > outputRun.txt

