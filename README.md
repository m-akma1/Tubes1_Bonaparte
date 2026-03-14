# Tugas Besar 1 IF2211 Strategi Algoritma

**Battle Code 2025 with Greedy Algorithm**

## Overview

## Algorithm Strategy

## General

### Objectives

The robot and tower's behavior are rules from their objective, a short term goals based on the current conditions. This objectives are sorted from the most high priority to the lowest, that is:
1. Building: try to build new towers
2. Exploring: explore unknown areas on the map
3. Fighting: attack enemy's robot or tower
4. Defending: defend and paint current area

The objective switching are specified based on the current condition in a robot. But, in general, the criteria is:
1. If there's a ruin that has not yet been build, switch to Building
2. If there's tiles in the map that's unknown, switch to Exploring
3. If there's enemy robot/towers nearby, switch to Fighting
4. Default to defend the current area.

### Map, Message, dan Area

Map defined as a 60 x 60 arrays of tiles. A tile consist of
* MapInfo info: the current tile state, that is:
    * MapLocation: the x and y location of the tile
    * Mark: what marker is on the tile
    * Paint: what paint is on the tile
    * HasRuin: whether there's a ruin on the tile
    * isPassable: whether the tile is passable
    * isResourcePatternCenter: whether the tile is a resource pattern center
    * isWall: whether the tile is a wall
* int recordedAt: in which turn the tile is recorded, used to determine which tile information is more up to date when receiving message from other robots. The higher the value, the more up to date the tile information is.
* int messagePriority: the priority of the tile information, used to determine which tile information is more important when sending message to other robots. The lower the value, the more important the tile information is.

Every robot and tower start with a map of NULL, which means that the tile is unknown. When a tile is explored, the tile information will be updated based on the recordedAt value. When a tower spawn a robot, it will prioritize broadcasting its map to that robot. If there's another robot sending message, the tile information will be updated based on the recordedAt value. If the map is already in rectangular shape (surrounded by wall) or already full, then all area is considered explored. The tile information will be encoded into 32 bit integer to be sent to other robots, with the following format:
* MapLocation [0-60] * 2            : 12 bit
* Mark [0-4]                        :  3 bit
* Paint [0-4]                       :  3 bit
* HasRuin [0/1]                     :  1 bit
* isPassable [0/1]                  :  1 bit
* isResourcePatternCenter [0/1]     :  1 bit
* isWall [0/1]                      :  1 bit
* recordedAt [0-1000]               : 10 bit
Total                               : 32 bit

An area is defined as all tiles that are within the boundary. The boundary is defined as the nearest wall from the current tower, or if there's no wall, is the farthest tile with radius 10 from the current tower. If there's another tower within that radius, then the area of both towers will be merged. Area will be used as the movement space of the robot when defending.

## Objective: Building

### Description

The main objective of building is to build new towers. The tower will be built on the ruin, and the pattern will be completed by the soldier and mopper. The tower will be built in the order of the nearest ruin to the current tower, and if there's a tie, the one with the most nearby ruins will be prioritized. The tower will be considered built if the chip rate suddenly drops, which means that the tower have been built and the chips have been used. After building a tower, the robot will switch to other objective based on the current condition.

### Tower Behavior

1. Spawning:
    * If chips < 1500, don't spawn anything.
    * Else, spawn new robot with rate:
        * (75 - chips / 25)% empty
        * (25 + chips / 25)% soldier
2. Find the nearest ruin that has not yet have any completed tower pattern. 
3. Send messages to one nearby soldier and mopper to switch objective to complete the pattern there.
4. If the chip rate suddenly drops, assume tower have been built.
5. Switch to other objective:
    * If there are other ruins, build that
    * If there there's still unknown area on the map, explore
    * If possible to raid enemy tower (the criteria will be defined later), raid
    * Else default to defending current area

### Robot Behavior

1. Find nearest ruins or coordinate from tower, pathfind there.
2. Complete the current tower pattern (soldier), or remove any enemy paint (mopper).
3. Defend the current ruins until a tower is spawned or timed out.

## Objective: Exploring

### Description

The main objective of exploring is to explore unknown areas on the map. The robot will be sent to random unknown tiles (NULL) in current maps or an emty area in it. The robot will be sent by the tower, and the tower will prioritize sending the robot to the nearest unknown tiles. The robot will explore the area until all bots are assigned a location to explore, or if there's a ruin that has not yet been build, switch to Building.

### Tower Behavior

1. Spawning:
    * If chips < 1000, don't spawn anything.
    * Else, spawn new robot with rate:
        * (50 - chips / 100)% empty
        * (25 + chips / 200)% soldier
        * (25 + chips / 200)% mopper
2. Find random unknown tiles (NULL) in current maps or an emty area in it. 
3. Broadcast that location to one soldier and one mopper to explore.
4. Repeat until all bots are assigned a location to explore.
5. If receive message that there's a ruin that has not yet been build, switch to Building.

### Robot Behavior

Sub-objective: Adventure
1. Recieve the target tiles from current tower.
2. Pathfind to there. Paint (soldier) or remove enemy paint (mopper) on any nearby tiles whenever possible.
3. If encounters a ruin, switch mode to building.
4. If encounters enemy robots:
    * If there's enemy paint nearby, return to the tower (retreat).
    * If there's allied paint or empty, switch mode to Fighting.
5. If the current paint is less than 50% (soldier) or 25% (mopper), return to tower (retreat).

Subobjective: Retreat
1. Create a circular ark back to the nearest tower location.
2. Pathfind to there and paint any nearby tiles whenever possible.
3. Prepare message to be sent to the tower with priority:
    1. Tiles with ruins/allied towers
    2. Enemy robot/tower last location 
    3. Walls
    4. Enemy tiles
    5. Allied tiles
    6. Allied robot
    7. Empty tiles
4. When in range to a tower, broadcast 5 most prioritized messages.
5. Switch to defending unless new objective are sent.

## Objective: Fighting

### Description

The main objective of Fighting is to attack enemy's robot or tower. The robot will be sent to the nearest enemy robot or tower, and will try to paint the area around it. The robot will switch to other objective based on the current condition, such as if there's a ruin that has not yet been build, switch to Building, if there's unknown area on the map, switch to Exploring, or if there's no enemy robot or tower nearby, switch to Defending.

### Tower Behavior

1. Spawning:
    * If chips < 1000, don't spawn anything.
    * Else, spawn new robot with rate:
        * (50 - chips / 100)% splasher
        * (25 + chips / 200)% soldier
        * (25 + chips / 200)% mopper
2. Find the nearest enemy robot or tower.
3. Broadcast that location to two splasher, one soldier, and one mopper to raid.
4. Repeat until any higher priority objective is triggered.

### Robot Behavior

1. Recieve the target location from current tower.
2. Pathfind to there, trying to go to allied paint whenever possible.
3. Paint (soldier) or remove enemy paint (mopper) on any nearby tiles while the current paint is above 75%.
4. When get to the enemy robot or tower, start attack formation:
    * Soldier: the front line, will try to be in the nearest tile to the enemy robot or tower, and will try to attack/paint the tower.
    * Splasher: the middle line, will go to the nearest enemy robot but keep a distance of 2 tiles from it, and will try to splash them.
    * Mopper: the back line, will try to remove enemy paints, refill soldier and splasher's paint, attack tower, and swing attack robots.
5. Switch to retreating if current paint is less than 25% or raid is successful, i.e enemy's tower/robot is destroyed.
6. Switch to other objective:
    * If there's a ruin that has not yet be build, switch to Building
    * Else retreat to tower and switch to defending

## Objective: Defending

### Description

The main objective of defending is to defend and paint current area in a circular pattern. The robot will try to paint the area around it, and will switch to other objective based on the current condition. If a robot is connected to a tower, it will try to complete special resource pattern to increate mining speed of the tower. Tower will try to upgrade itself while strategizing about the current map condition.

### Tower Behavior

1. If the current level is 1:
    * Stop spawning new robots until able to upgrade to level 2.
    * Switch to default spawn rate.
2. If current level is 2:
    * If just upgraded to level 2, switch to default spawn rate.
    * If last upgraded is more than 100 turns ago, stop spawning new robots until able to upgrade to level 3.
3. If current level is 3, use default spawn rate:
    * (20 - chip / 1200 - turns / 600)% empty
    * (30 + chip / 400 + turns / 200)% soldier
    * (30 + chip / 400 + turns / 200)% mopper
    * (20 + chip / 400 + turns / 200)% splasher
4. If there's nearby robot with low paint, refill it.
5. If is not the center of a special resource pattern, assign a soldier to complete it.

### Robot Behavior

1. If low on paint, try to get paint from the tower or nearby mopper.
2. If assigned to complete a special resource pattern, try to complete it.
3. Default to patrol in a circular pattern around the tower in radius of 5.

## Credits

Made by 

| Name                       | NIM      | Handle                                             |
|----------------------------|----------|----------------------------------------------------|
| Moh. Hafizh Irham Perdana  | 13524025 | [@hafizhperdana](https://github.com/hafizhperdana) |
| Muhammad Jordan Ferimeison | 13524047 | [@Nyanist](https://github.com/Nyanist)             |
| Muhammad Akmal             | 13524099 | [@m-akma1](https://github.com/m-akma1)             |

For IF2211 Algorithm Strategy  
Semester II AY 2025/2026  

**Informatics Study Program**  
**School of Electrical Engineering and Informatics**  
**Bandung Institute of Technology**

---

## Battlecode 2025 Scaffold - Java

This is the Battlecode 2025 Java scaffold, containing an `examplefuncsplayer`. Read https://play.battlecode.org/bc25java/quick_start !


### Project Structure

- `README.md`
    This file.
- `build.gradle`
    The Gradle build file used to build and run players.
- `src/`
    Player source code.
- `test/`
    Player test code.
- `client/`
    Contains the client. The proper executable can be found in this folder (don't move this!)
- `build/`
    Contains compiled player code and other artifacts of the build process. Can be safely ignored.
- `matches/`
    The output folder for match files.
- `maps/`
    The default folder for custom maps.
- `gradlew`, `gradlew.bat`
    The Unix (OS X/Linux) and Windows versions, respectively, of the Gradle wrapper. These are nifty scripts that you can execute in a terminal to run the Gradle build tasks of this project. If you aren't planning to do command line development, these can be safely ignored.
- `gradle/`
    Contains files used by the Gradle wrapper scripts. Can be safely ignored.

### How to get started

You are free to directly edit `examplefuncsplayer`.
However, we recommend you make a new bot by copying `examplefuncsplayer` to a new package under the `src` folder.

### Useful Commands

- `./gradlew build`
    Compiles your player
- `./gradlew run`
    Runs a game with the settings in gradle.properties
- `./gradlew update`
    Update configurations for the latest version -- run this often
- `./gradlew zipForSubmit`
    Create a submittable zip file
- `./gradlew tasks`
    See what else you can do!


### Configuration 

Look at `gradle.properties` for project-wide configuration.

If you are having any problems with the default client, please report to teh devs and
feel free to set the `compatibilityClient` configuration to `true` to download a different version of the client.
