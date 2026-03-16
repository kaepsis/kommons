package dev.kaepsis.kommons.chat;

public interface ColorStrategy {

    String color(String message);
    String stripColors(String message);

}
