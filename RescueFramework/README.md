# Rescue Robots

A simplified rescue framework using Jason agent technology. This project implements a multi-agent rescue simulation with firefighter and cleaner robots.

## Running the Project

To run this project, navigate to the `src` directory and use the Jason command:

```bash
cd src
jason RescueRobots.mas2j
```

## Project Structure

- `RescueRobots.mas2j`: The main project configuration file
- `RescueEnv.java`: The environment class that simulates the rescue world
- `firefighter.asl`: The firefighter agent implementation
- `cleaner.asl`: The cleaner agent implementation

## Agents

This system contains two types of agents:

1. **Firefighter** - Searches for and extinguishes fires
2. **Cleaner** - Searches for and cleans up junk

The agents autonomously explore unknown parts of the environment, detect fires or junk, and handle them.

## Environment

The environment is represented as a grid where:

- 🔥 Fires appear with different intensities
- 🗑️ Junk appears in different types (junk10, junk20)
- ❓ Unknown areas need to be explored

The firefighter robot can reduce the intensity of fires, and the cleaner robot can remove junk.
