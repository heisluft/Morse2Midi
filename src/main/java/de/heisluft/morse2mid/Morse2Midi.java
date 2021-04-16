package de.heisluft.morse2mid;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.midi.spi.MidiFileWriter;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public class Morse2Midi {

  private static final int TICKS_PER_QUARTER = 32;
  private static final int _64TH_TICKS = TICKS_PER_QUARTER / 16;

  public static void main(String[] args) throws IOException, InvalidMidiDataException {
    Map<Integer, String> mappings = new HashMap<>();
    Arrays.stream(new String(Objects.requireNonNull(
        Morse2Midi.class.getResourceAsStream("/morse-mapping")
    ).readAllBytes(), StandardCharsets.US_ASCII).split("\n")).map(String::toCharArray)
        .forEach(arr -> mappings.put((int)arr[0], new String(arr, 2, arr.length - 2)));

    Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
    Track t = sequence.createTrack();

    int currentTick = 0;

    try(InputStream is = new BufferedInputStream(System.in)) {
      int r = 0;
      while((r = is.read())  != -1) {
        if(r == '\r') continue; //skip carriage return
        if(r == ' ' || r == '\n' || r == '\t') currentTick += _64TH_TICKS * 7; // The space between words is seven units.
        else {
          int ucase = Character.toUpperCase(r);
          if(mappings.containsKey(ucase)){
            for(char c : mappings.get(ucase).toCharArray()) {
              t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 48, 127), currentTick));
              currentTick += _64TH_TICKS * (c == 'L' ? 3 : 1); // The length of a dot / S is one unit, a dash / L is 3 units
              t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 48, 0), currentTick));
              currentTick += _64TH_TICKS; // The space between parts of the same letter is one unit
            } currentTick += _64TH_TICKS * 2; //The space between letters is three units, but we add one unit after each char anyway, so only 2 are added here
          }else System.out.println("Unknown char: " + (char) r); //This will happen a lot with non text docs
        }
      }
    }

    try(OutputStream os = Files.newOutputStream(Path.of(args.length == 0 ? "out.mid" : args[0]))) {
      ServiceLoader.load(MidiFileWriter.class).findFirst().get().write(sequence, 0, os);
    }
  }
}
