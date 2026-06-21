package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpAddressFilterTest {

  private IpAddressFilter filter;

  @BeforeEach
  void setUp() {
    filter = new IpAddressFilter();
  }

  @Test
  void validIp_detected() {
    List<Span> spans = filter.find("server at 192.168.1.1 running");

    assertEquals(1, spans.size());
    assertEquals("192.168.1.1", spans.get(0).value());
    assertEquals(PiiType.IP_ADDRESS, spans.get(0).type());
  }

  @Test
  void localhost_detected() {
    List<Span> spans = filter.find("connect to 127.0.0.1:8080");

    assertEquals(1, spans.size());
    assertEquals("127.0.0.1", spans.get(0).value());
  }

  @Test
  void broadcast_detected() {
    List<Span> spans = filter.find("broadcast 255.255.255.255");

    assertEquals(1, spans.size());
    assertEquals("255.255.255.255", spans.get(0).value());
  }

  @Test
  void octetOver255_notDetected() {
    List<Span> spans = filter.find("invalid 256.1.1.1 address");

    assertTrue(spans.isEmpty());
  }

  @Test
  void multipleIps_allDetected() {
    List<Span> spans = filter.find("from 10.0.0.1 to 10.0.0.2");

    assertEquals(2, spans.size());
    assertEquals("10.0.0.1", spans.get(0).value());
    assertEquals("10.0.0.2", spans.get(1).value());
  }
}
