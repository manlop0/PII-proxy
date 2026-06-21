package com.project.piiproxy.pipeline.anonymize;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import com.project.piiproxy.pipeline.state.PiiStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PiiSubstitutorTest {

  private PiiStorage storage;
  private PiiSubstitutor substitutor;

  @BeforeEach
  void setUp() {
    storage = Mockito.mock(PiiStorage.class);
    substitutor = new PiiSubstitutor(storage);
  }

  @Test
  void singleEntity_replacedWithTag() {
    when(storage.saveOriginal(eq("sess"), eq("EMAIL"), eq("test@mail.com")))
        .thenReturn("<EMAIL_1>");

    String result = substitutor.substitute("my email is test@mail.com", "sess",
        new ArrayList<>(List.of(new Span(12, 25, PiiType.EMAIL, "EMAIL", "test@mail.com"))));

    assertEquals("my email is <EMAIL_1>", result);
  }

  @Test
  void multipleEntities_correctTags() {
    when(storage.saveOriginal(eq("sess"), eq("EMAIL"), eq("first@mail.com")))
        .thenReturn("<EMAIL_1>");
    when(storage.saveOriginal(eq("sess"), eq("EMAIL"), eq("second@mail.com")))
        .thenReturn("<EMAIL_2>");

    String result = substitutor.substitute("first@mail.com and second@mail.com", "sess",
        new ArrayList<>(List.of(
            new Span(0, 14, PiiType.EMAIL, "EMAIL", "first@mail.com"),
            new Span(19, 34, PiiType.EMAIL, "EMAIL", "second@mail.com")
        )));

    assertEquals("<EMAIL_1> and <EMAIL_2>", result);
  }

  @Test
  void emptySpans_originalTextReturned() {
    String result = substitutor.substitute("no pii here", "sess", List.of());

    assertEquals("no pii here", result);
    verifyNoInteractions(storage);
  }

  @Test
  void differentTypes_separateCounters() {
    when(storage.saveOriginal(eq("sess"), eq("EMAIL"), eq("a@b.com")))
        .thenReturn("<EMAIL_1>");
    when(storage.saveOriginal(eq("sess"), eq("PHONE"), eq("1234567890")))
        .thenReturn("<PHONE_1>");

    String result = substitutor.substitute("email a@b.com phone 1234567890", "sess",
        new ArrayList<>(List.of(
            new Span(6, 13, PiiType.EMAIL, "EMAIL", "a@b.com"),
            new Span(20, 30, PiiType.PHONE, "PHONE", "1234567890")
        )));

    assertEquals("email <EMAIL_1> phone <PHONE_1>", result);
  }

  @Test
  void backwardReplacement_indicesCorrect() {
    when(storage.saveOriginal(eq("sess"), eq("PERSON"), eq("Alice")))
        .thenReturn("<PERSON_1>");
    when(storage.saveOriginal(eq("sess"), eq("PERSON"), eq("Bob")))
        .thenReturn("<PERSON_2>");

    // "Alice met Bob" — Alice at [0,5], Bob at [10,13]
    String result = substitutor.substitute("Alice met Bob", "sess",
        new ArrayList<>(List.of(
            new Span(0, 5, PiiType.MODEL, "PERSON", "Alice"),
            new Span(10, 13, PiiType.MODEL, "PERSON", "Bob")
        )));

    assertEquals("<PERSON_1> met <PERSON_2>", result);
  }

  @Test
  void modelType_usesRawType() {
    when(storage.saveOriginal(eq("sess"), eq("LOCATION"), eq("Berlin")))
        .thenReturn("<LOCATION_1>");

    String result = substitutor.substitute("city Berlin", "sess",
        new ArrayList<>(List.of(new Span(5, 11, PiiType.MODEL, "LOCATION", "Berlin"))));

    assertEquals("city <LOCATION_1>", result);
    verify(storage).saveOriginal("sess", "LOCATION", "Berlin");
  }
}
