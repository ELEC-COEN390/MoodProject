package com.example.moodproject;

public class UserPreferences {
    private boolean music;
    private boolean sports;
    private boolean food;
    private boolean health;
    private boolean arts;
    private boolean travel;

    // Default constructor required for Firebase
    public UserPreferences() {
    }

    public UserPreferences(boolean music, boolean sports, boolean food, boolean health, boolean arts, boolean travel) {
        this.music = music;
        this.sports = sports;
        this.food = food;
        this.health = health;
        this.arts = arts;
        this.travel = travel;
    }

    // Getters and setters
    public boolean isMusic() {
        return music;
    }

    public void setMusic(boolean music) {
        this.music = music;
    }

    public boolean isSports() {
        return sports;
    }

    public void setSports(boolean sports) {
        this.sports = sports;
    }

    public boolean isFood() {
        return food;
    }

    public void setFood(boolean food) {
        this.food = food;
    }

    public boolean isHealth() {
        return health;
    }

    public void setHealth(boolean health) {
        this.health = health;
    }

    public boolean isArts() {
        return arts;
    }

    public void setArts(boolean arts) {
        this.arts = arts;
    }

    public boolean isTravel() {
        return travel;
    }

    public void setTravel(boolean travel) {
        this.travel = travel;
    }
}