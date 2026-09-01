/**
 * TimeCode
 * Copyright (C) 2022 NonStatic
 *
 * This file is part of timecode.
 * timecode is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *  is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with . If not, see <https://www.gnu.org/licenses/>.
 */
package eu.nonstatic.timecode;

import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeCodeTest {

  @Test
  void should_build_from_seconds() {
    assertEquals("12:25:00", TimeCode.ofSeconds(745L).toString());
    assertEquals("00:00:00", TimeCode.ofSeconds(0L).toString());
    assertEquals("00:59:00", TimeCode.ofSeconds(59L).toString());
    assertEquals("01:00:00", TimeCode.ofSeconds(60L).toString());
    assertEquals("100:00:00", TimeCode.ofSeconds(6_000L).toString()); // beyond a CDR length

    assertEquals(TimeCode.ofMillis(745_000L), TimeCode.ofSeconds(745L));
    assertEquals(0, TimeCode.ofSeconds(745L).getFrames()); // whole seconds never carry frames
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofSeconds(-1L));

  }

  @Test
  void should_build_from_millis() {
    TimeCode timeCode = TimeCode.ofMillis(620400L, TimeCode.DEFAULT_ROUNDING);
    assertEquals("10:20:30", timeCode.toString());
  }

  @Test
  void should_build_from_millis_with_rounding() {
    assertEquals("00:00:00", TimeCode.ofMillis(0L).toString());
    assertEquals("10:20:30", TimeCode.ofMillis(620_400L).toString());
    assertEquals("100:00:00", TimeCode.ofMillis(6_000_000L).toString());

    // 413ms into the second == frame 30.975
    assertEquals("10:20:31", TimeCode.ofMillis(620_413L, TimeCodeRounding.CLOSEST).toString());
    assertEquals("10:20:30", TimeCode.ofMillis(620_413L, TimeCodeRounding.DOWN).toString());
    assertEquals("10:20:31", TimeCode.ofMillis(620_413L, TimeCodeRounding.UP).toString());

    assertEquals(TimeCodeRounding.DOWN, TimeCode.ofMillis(620_413L, TimeCodeRounding.DOWN).getRounding());
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofMillis(-1_000L));
  }

  @Test
  void should_build_from_nanos() {
    assertEquals("00:00:00", TimeCode.ofNanos(0L).toString());
    assertEquals("10:20:30", TimeCode.ofNanos(620_400_000_000L).toString());
    assertEquals("100:00:00", TimeCode.ofNanos(6_000_000_000_000L).toString());
    assertEquals("00:00:74", TimeCode.ofNanos(986_666_667L).toString());

    assertEquals(TimeCode.ofMillis(620_400L), TimeCode.ofNanos(620_400_000_000L));
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofNanos(-1_000_000_000L));
  }

  @Test
  void should_build_from_nanos_with_rounding() {
    // 500ms into the second == frame 37.5
    assertEquals("00:01:38", TimeCode.ofNanos(1_500_000_000L, TimeCodeRounding.CLOSEST).toString());
    assertEquals("00:01:37", TimeCode.ofNanos(1_500_000_000L, TimeCodeRounding.DOWN).toString());
    assertEquals("00:01:38", TimeCode.ofNanos(1_500_000_000L, TimeCodeRounding.UP).toString());

    // 400000001ns into the second == frame 30.000000075
    assertEquals("00:01:30", TimeCode.ofNanos(1_400_000_001L, TimeCodeRounding.CLOSEST).toString());
    assertEquals("00:01:30", TimeCode.ofNanos(1_400_000_001L, TimeCodeRounding.DOWN).toString());
    assertEquals("00:01:31", TimeCode.ofNanos(1_400_000_001L, TimeCodeRounding.UP).toString());

    assertEquals(TimeCodeRounding.UP, TimeCode.ofNanos(1_500_000_000L, TimeCodeRounding.UP).getRounding());

    // null rounding falls back to the default one
    assertEquals("00:01:38", TimeCode.ofNanos(1_500_000_000L, null).toString());
  }

  @Test
  void should_build_from_duration() {
    TimeCode timeCode = new TimeCode(Duration.ofMillis(620400L), TimeCode.DEFAULT_ROUNDING);
    assertEquals("10:20:30", timeCode.toString());
  }

  @Test
  void should_build_from_mmssff() {
    TimeCode timeCode = new TimeCode(10, 20, 30);
    assertEquals("10:20:30", timeCode.toString());
  }

  @Test
  void should_build_from_other() {
    TimeCode timeCode = new TimeCode(new TimeCode(10, 20, 30));
    assertEquals("10:20:30", timeCode.toString());
  }

  @Test
  void should_build_from_frames() {
    assertEquals("15:24:66", TimeCode.ofFrames(69366).toString());
  }

  @Test
  void should_build_zero() {
    TimeCode timeCode = TimeCode.ZERO_SECOND;
    assertEquals("00:00:00", timeCode.toString());
  }

  @Test
  void should_not_build() {
    assertThrows(IllegalArgumentException.class, () -> new TimeCode(-1, 0, 0));
    assertDoesNotThrow(() -> new TimeCode(100, 0, 0));

    assertThrows(IllegalArgumentException.class, () -> new TimeCode(0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new TimeCode(0, 60, 0));

    assertThrows(IllegalArgumentException.class, () -> new TimeCode(0, 0, -1));
    assertThrows(IllegalArgumentException.class, () -> new TimeCode(0, 0, 75));
  }

  @Test
  void should_parse() {
    assertEquals("10:20:30", TimeCode.parse("10:20:30").toString());
    assertThrows(IllegalArgumentException.class, () -> TimeCode.parse("10:20:75"));

    assertEquals("10:20:30", TimeCode.parse("10:20:30", true).toString());

    // more than 99 minutes is allowed. And they're laid out on minutes only; 1:43:20:30 won't be accepted by players.
    assertEquals("103:20:30", TimeCode.parse("103:20:30", true).toString());
  }

  @Test
  void should_not_parse() {
    assertThrows(IllegalArgumentException.class, () -> TimeCode.parse("10:20:75"));
    assertThrows(IllegalArgumentException.class, () -> TimeCode.parse("10:20:75", false));
  }

  @Test
  void should_parse_lenient() {
    TimeCode timeCode = TimeCode.parse("10:20:75", true);
    assertEquals("10:20:56", timeCode.toString());
  }

  @Test
  void should_wither() {
    TimeCode timeCode = new TimeCode(10, 20, 30);
    assertEquals("11:20:30", timeCode.withMinutes(11).toString());
    assertEquals("10:21:30", timeCode.withSeconds(21).toString());
    assertEquals("10:20:31", timeCode.withFrames(31).toString());
  }

  @Test
  void should_diff() {
    TimeCode timeCode = new TimeCode(10, 20, 30);
    assertEquals(Duration.ofNanos(60_000_000_000L+1_000_000_000L+13_333_333L), timeCode.until(new TimeCode(11, 21, 31)));

    assertEquals(new TimeCode(9, 20, 30), timeCode.minus(Duration.ofMinutes(1)));
    assertEquals(Duration.ofNanos(374_933_333_333L), timeCode.minus(new TimeCode(4, 5, 35)));
    assertEquals(new TimeCode(10, 15, 62), timeCode.minusMillis(4576L));
    assertEquals(new TimeCode(10, 25, 30), timeCode.plus(Duration.ofSeconds(5)));
    assertEquals(new TimeCode(10, 24, 73), timeCode.plusMillis(4576L));
  }

  // conversions

  @Test
  void should_convert() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertEquals(10, timeCode.getMinutes());
    assertEquals(20, timeCode.getSeconds());
    assertEquals(35, timeCode.getFrames());
    assertEquals(46_535, timeCode.toFrames());
    assertEquals(620L, timeCode.toSeconds()); // rounded
    assertEquals(620_467L, timeCode.toMillis()); // rounded
    assertEquals(467L, timeCode.toMillisPart()); // rounded
    assertEquals(620_466_666_667L, timeCode.toNanos());
    assertEquals(466_666_667L, timeCode.toNanosPart());
    assertEquals(Duration.ofNanos(620_466_666_667L), timeCode.toDuration());
  }

  @Test
  void should_convert_to_seconds() {
    assertEquals(0L, TimeCode.ZERO_SECOND.toSeconds());
    assertEquals(0L, new TimeCode(0, 0, 74).toSeconds()); // frames are dropped
    assertEquals(60L, new TimeCode(1, 0, 0).toSeconds());
    assertEquals(620L, new TimeCode(10, 20, 35).toSeconds());
    assertEquals(2_400_000_000L, new TimeCode(40_000_000, 0, 0).toSeconds()); // no int overflow
  }

  @Test
  void should_convert_to_nanos() {
    assertEquals(0L, TimeCode.ZERO_SECOND.toNanos());
    assertEquals(1_000_000_000L, TimeCode.ONE_SECOND.toNanos());
    assertEquals(986_666_667L, new TimeCode(0, 0, 74).toNanos());
    assertEquals(620_466_666_667L, new TimeCode(10, 20, 35).toNanos());
    assertEquals(2_400_000_000_000_000_000L, new TimeCode(40_000_000, 0, 0).toNanos()); // no int overflow
  }

  @Test
  void should_convert_to_millis_part() {
    assertEquals(0L, new TimeCode(0, 0, 0).toMillisPart());
    assertEquals(13L, new TimeCode(0, 0, 1).toMillisPart());
    assertEquals(467L, new TimeCode(10, 20, 35).toMillisPart());
    assertEquals(493L, new TimeCode(0, 0, 37).toMillisPart());
    assertEquals(507L, new TimeCode(0, 0, 38).toMillisPart());
    assertEquals(987L, new TimeCode(0, 0, 74).toMillisPart()); // stays below a full second
  }

  @Test
  void should_convert_to_nanos_part() {
    assertEquals(0L, new TimeCode(0, 0, 0).toNanosPart());
    assertEquals(13_333_333L, new TimeCode(0, 0, 1).toNanosPart());
    assertEquals(400_000_000L, new TimeCode(0, 0, 30).toNanosPart());
    assertEquals(466_666_667L, new TimeCode(10, 20, 35).toNanosPart()); // independent of minutes/seconds
    assertEquals(986_666_667L, new TimeCode(0, 0, 74).toNanosPart()); // stays below a full second
  }


  // arithmetic

  @Test
  void should_plus_nanos() {
    TimeCode timeCode = new TimeCode(10, 20, 30); // == 620_400_000_000ns

    assertEquals(new TimeCode(10, 20, 30), timeCode.plusNanos(0L));
    assertEquals(new TimeCode(10, 20, 31), timeCode.plusNanos(13_333_334L)); // one frame
    assertEquals(new TimeCode(10, 21, 30), timeCode.plusNanos(1_000_000_000L));
    assertEquals(new TimeCode(11, 20, 30), timeCode.plusNanos(60_000_000_000L));
    assertEquals(new TimeCode(10, 21, 0), timeCode.plusNanos(600_000_000L)); // frames carry over to seconds
    assertEquals(new TimeCode(10, 20, 15), timeCode.plusNanos(-200_000_000L)); // negative amounts go backwards

    assertThrows(IllegalArgumentException.class, () -> timeCode.plusNanos(-621_400_000_000L)); // below zero
  }

  @Test
  void should_minus_nanos() {
    TimeCode timeCode = new TimeCode(10, 20, 30); // == 620_400_000_000ns

    assertEquals(new TimeCode(10, 20, 30), timeCode.minusNanos(0L));
    assertEquals(new TimeCode(10, 20, 29), timeCode.minusNanos(13_333_334L)); // one frame
    assertEquals(new TimeCode(10, 19, 30), timeCode.minusNanos(1_000_000_000L));
    assertEquals(new TimeCode(10, 20, 0), timeCode.minusNanos(400_000_000L));
    assertEquals(TimeCode.ZERO_SECOND, timeCode.minusNanos(620_400_000_000L));
    assertEquals(new TimeCode(10, 20, 45), timeCode.minusNanos(-200_000_000L)); // negative amounts go forwards

    assertThrows(IllegalArgumentException.class, () -> timeCode.minusNanos(621_400_000_000L)); // below zero
  }

  @Test
  void should_keep_rounding_across_nanos_arithmetic() {
    // 1_400_000_001ns == frame 30.000000075
    assertEquals("00:01:30", TimeCode.ofNanos(0L, TimeCodeRounding.CLOSEST).plusNanos(1_400_000_001L).toString());
    assertEquals("00:01:30", TimeCode.ofNanos(0L, TimeCodeRounding.DOWN).plusNanos(1_400_000_001L).toString());
    assertEquals("00:01:31", TimeCode.ofNanos(0L, TimeCodeRounding.UP).plusNanos(1_400_000_001L).toString());

    // 2_000_000_000 - 500_000_000 == 1.5s == frame 37.5
    assertEquals("00:01:38", TimeCode.ofNanos(2_000_000_000L, TimeCodeRounding.CLOSEST).minusNanos(500_000_000L).toString());
    assertEquals("00:01:37", TimeCode.ofNanos(2_000_000_000L, TimeCodeRounding.DOWN).minusNanos(500_000_000L).toString());
    assertEquals("00:01:38", TimeCode.ofNanos(2_000_000_000L, TimeCodeRounding.UP).minusNanos(500_000_000L).toString());

    assertEquals(TimeCodeRounding.DOWN, TimeCode.ofNanos(0L, TimeCodeRounding.DOWN).plusNanos(1L).getRounding());
    assertEquals(TimeCodeRounding.UP, TimeCode.ofNanos(2_000_000_000L, TimeCodeRounding.UP).minusNanos(500_000_000L).getRounding());
  }

  @Test
  void should_overflow_frames_when_rounding_at_the_very_end_of_a_second() {
    // 999_999_999ns == frame 74.999999925, which rounds to a 75th frame the mm:ss:ff form cannot hold
    assertEquals("00:00:74", TimeCode.ofNanos(999_999_999L, TimeCodeRounding.DOWN).toString());
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofNanos(999_999_999L, TimeCodeRounding.CLOSEST));
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofNanos(999_999_999L, TimeCodeRounding.UP));

    // CLOSEST only tips over past the half-frame mark, UP tips over on any remainder
    assertEquals("00:00:74", TimeCode.ofNanos(993_333_333L, TimeCodeRounding.CLOSEST).toString()); // frame 74.499999975
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofNanos(993_333_334L, TimeCodeRounding.CLOSEST)); // frame 74.50000005
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofNanos(986_666_668L, TimeCodeRounding.UP)); // frame 74.0000001

    // and it propagates to the arithmetic, since it goes through the same ctor
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofNanos(2_000_000_000L, TimeCodeRounding.UP).minusNanos(1L));
    assertThrows(IllegalArgumentException.class, () -> TimeCode.ofMillis(999L, TimeCodeRounding.UP));
  }

  @Test
  void should_roundtrip_nanos_arithmetic() {
    TimeCode timeCode = new TimeCode(10, 20, 30);
    assertEquals(timeCode, timeCode.plusNanos(1_000_000_000L).minusNanos(1_000_000_000L));
    assertEquals(timeCode, TimeCode.ofNanos(timeCode.toNanos()));
  }


  // TemporalAmount contract

  @Test
  void should_get_units() {
    List<TemporalUnit> units = new TimeCode(10, 20, 35).getUnits();
    assertEquals(List.of(SECONDS, NANOS), units);

    assertSame(units, TimeCode.ZERO_SECOND.getUnits()); // shared immutable instance
    assertThrows(UnsupportedOperationException.class, () -> units.add(ChronoUnit.MINUTES));
  }

  @Test
  void should_get() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertEquals(20L, timeCode.get(SECONDS)); // the seconds field only, minutes are not folded in
    assertEquals(466_666_667L, timeCode.get(NANOS));

    assertEquals(0L, TimeCode.ZERO_SECOND.get(SECONDS));
    assertEquals(0L, TimeCode.ZERO_SECOND.get(NANOS));
    assertEquals(986_666_667L, new TimeCode(0, 0, 74).get(NANOS));
  }

  @Test
  void should_not_get_unsupported_unit() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertThrows(UnsupportedTemporalTypeException.class, () -> timeCode.get(ChronoUnit.MINUTES));
    assertThrows(UnsupportedTemporalTypeException.class, () -> timeCode.get(ChronoUnit.MILLIS));
    assertThrows(UnsupportedTemporalTypeException.class, () -> timeCode.get(ChronoUnit.HOURS));
    assertThrows(UnsupportedTemporalTypeException.class, () -> timeCode.get(null));
  }

  @Test
  void should_build_duration_from_temporal_amount() {
    // exercises getUnits() + get() through the JDK
    assertEquals(Duration.ofSeconds(20L, 466_666_667L), Duration.from(new TimeCode(10, 20, 35)));
  }

  @Test
  void should_add_to() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertEquals(LocalTime.of(1, 2, 23, 466_666_667), timeCode.addTo(LocalTime.of(1, 2, 3)));
    assertEquals(Instant.ofEpochSecond(20L, 466_666_667L), timeCode.addTo(Instant.EPOCH));

    // only the seconds and frames fields are added, minutes are ignored
    assertEquals(LocalTime.NOON, new TimeCode(5, 0, 0).addTo(LocalTime.NOON));
    assertSame(LocalTime.NOON, TimeCode.ZERO_SECOND.addTo(LocalTime.NOON));

    assertEquals(LocalTime.of(0, 0, 20), new TimeCode(0, 20, 0).addTo(LocalTime.MIDNIGHT)); // seconds branch only
    assertEquals(LocalTime.of(0, 0, 0, 400_000_000), new TimeCode(0, 0, 30).addTo(LocalTime.MIDNIGHT)); // nanos branch only
  }

  @Test
  void should_not_add_to_unsupported_temporal() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertThrows(UnsupportedTemporalTypeException.class, () -> timeCode.addTo(LocalDate.EPOCH));
  }

  @Test
  void should_subtract_from() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertEquals(LocalTime.of(1, 1, 42, 533_333_333), timeCode.subtractFrom(LocalTime.of(1, 2, 3)));
    assertEquals(Instant.parse("2021-12-31T23:59:39.533333333Z"),
        timeCode.subtractFrom(Instant.parse("2022-01-01T00:00:00Z")));

    // only the seconds and frames fields are subtracted, minutes are ignored
    assertEquals(LocalTime.NOON, new TimeCode(5, 0, 0).subtractFrom(LocalTime.NOON));
    assertSame(LocalTime.NOON, TimeCode.ZERO_SECOND.subtractFrom(LocalTime.NOON));

    assertEquals(LocalTime.of(11, 59, 40), new TimeCode(0, 20, 0).subtractFrom(LocalTime.NOON)); // seconds branch only
    assertEquals(LocalTime.of(11, 59, 59, 600_000_000), new TimeCode(0, 0, 30).subtractFrom(LocalTime.NOON)); // nanos branch only
  }

  @Test
  void should_not_subtract_from_unsupported_temporal() {
    TimeCode timeCode = new TimeCode(10, 20, 35);
    assertThrows(UnsupportedTemporalTypeException.class, () -> timeCode.subtractFrom(LocalDate.EPOCH));
  }

  // other

  @Test
  void should_equal() {
    TimeCode timeCode1 = new TimeCode(10, 20, 30);

    assertEquals(timeCode1, timeCode1);
    assertEquals(timeCode1, TimeCode.ofMillis(620400L, TimeCode.DEFAULT_ROUNDING));
    assertNotEquals(new TimeCode(30, 20, 10), timeCode1);
    assertNotEquals("whatever", timeCode1); // using equals for coverage
    assertNotEquals(null, timeCode1); // using equals for coverage
  }

  @Test
  void should_not_equal() {
    TimeCode timeCode1 = new TimeCode(10, 20, 30);
    assertNotEquals(null, timeCode1);
    assertNotEquals("whatever", timeCode1);
  }

  @Test
  void should_hash() {
    assertEquals(46530, new TimeCode(10, 20, 30).hashCode());
  }
}
