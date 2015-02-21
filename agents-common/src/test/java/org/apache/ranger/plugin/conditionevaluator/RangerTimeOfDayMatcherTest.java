/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.plugin.conditionevaluator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemCondition;
import org.apache.ranger.plugin.policyengine.RangerAccessRequest;
import org.junit.Test;

import com.google.common.collect.Lists;

public class RangerTimeOfDayMatcherTest {

	final Pattern p = RangerTimeOfDayMatcher._Pattern;

	@Test
	public void test_patterMatching_happyPath() {
		// sensible values and some goofy ones work
		String[] durations = new String[] { 
				"9am-5pm", " 9Am -5 Pm", " 9Am -5 Pm", "9 AM -5 p.m.", "9a.M - 5Pm.",
				"9:30am-5:30pm", " 9:00Am -5:59 Pm",
				"   9   am   -  4 pm  "
		};
		check(durations, true);
	}
	
	@Test
	public void test_patterMatching_unexpectedMatches() {
		// even semantically illegal durations work -- parsing is just for format not for semantics!
		String[] durations = new String[] {
				"9pm-5AM",   // illegal duration where start > end is allows parsed as right!
				"00PM-44PM",  // any two digits are allowed!
				"9:0am-5:7pm", // Minute part can be one digit
		};
		check(durations, true);
	}
	
	@Test
	public void test_patterMatching_misMatches() {
		// clearly invalid values don't match.
		String[] durations = new String[] {
				"9am", "-", "", "9-5", "9-10am", "9pm-6",
				"009AM-5AM", // only 2 digits allowed
				"9am-5p m", // space betweem am or pm are not allowed
				"9:am-5:30pm", // if there is a : then minutes part must follow!
				"9:00am-5:300pm", // Minutes part is limited to 2 digits
				"9: 00am-5 :300pm", // No space allowed around the :
				};
		check(durations, false);
	}	

	void check(String[] durations, boolean match) {
		for (String aDuration : durations) {
			Matcher matcher = p.matcher(aDuration);
			if (match) {
				assertTrue(aDuration, matcher.matches());
			} else {
				assertFalse(aDuration, matcher.matches());
			}
		}
	}

	@Test
	public void test_patterMatching_happyPath_groups() {
		// groups returned by matcher are right
		String[][] input = new String[][] {
				{ "9am-5pm", "9", null, "a", "5", null, "p"},
				{ "9Am -5 pM", "9", null, "A", "5", null, "p"},
				{ "9 AM -5 p.m.", "9", null, "A", "5", null, "p" },
				{ "9:30AM - 5:15pm", "9", "30", "A", "5", "15", "p" },
				{ "9:30 AM - 5:15 p.m.", "9", "30", "A", "5", "15", "p" },
		};
		checkGroups(input);
	}
	
	void checkGroups(String[][] input) {
		for (String[] data : input) {
			Matcher m = p.matcher(data[0]);
			assertTrue(data[0], m.matches());
			assertEquals(8, m.groupCount());
			assertEquals(data[1], m.group(1));
			assertEquals(data[2], m.group(3));
			assertEquals(data[3], m.group(4));
			assertEquals(data[4], m.group(5));
			assertEquals(data[5], m.group(7));
			assertEquals(data[6], m.group(8));
		}
	}

	@Test
	public void test_ExtractDuration_happyPath() {
		RangerTimeOfDayMatcher matcher = new RangerTimeOfDayMatcher();
		Object[][] input = new Object[][] {
				{ "9am-5pm", true, 9*60, (12+5)*60 },
				{ "1 PM - 10P.M.", true, (12+1)*60, (12+10)*60 },
				{ "1PM - 9AM", false, null, null }, // illegal duration should come back as null
				{ "1PM", false, null, null }, // illegal patterns should come back as null, too
		};
		for (Object[] data: input) {
			int[] duration = matcher.extractDuration((String)data[0]);
			boolean expectedToMatch = (boolean)data[1];
			if (expectedToMatch) {
				int start = (Integer)data[2];
				int end = (Integer)data[3];
				assertArrayEquals(new int[] { start, end }, duration);
			} else {
				assertNull(duration);
			}
		}
	}
	
	@Test
	public void test_durationsMatched() {
		List<int[]> durations = Lists.newArrayList(
				new int[]{2*60, 7*60},   // 2am-7am
				new int[]{9*60, 17*60}); // 9am-5pm
		RangerTimeOfDayMatcher matcher = new RangerTimeOfDayMatcher();
		Object[][] input = new Object[][] {
				{ 1, false },
				{ 2, true },
				{ 3, true },
				{ 7, true },
				{ 8, false },
				{9, true },
				{10, true },
				{16, true}, 
				{17, true}, 
				{18, false },
				{23, false },
		};
		for (Object[] data : input) {
			int hour = (int)data[0];
			boolean matchExpected = (boolean)data[1];
			boolean result = matcher.durationMatched(durations, hour, 0);
			if (matchExpected) {
				assertTrue("" + hour, result);
			} else {
				assertFalse("" + hour, result);
			}
		}
	}
	
	@Test
	public void test_end2end_happyPath() {
		RangerPolicyItemCondition itemCondition = mock(RangerPolicyItemCondition.class);
		when(itemCondition.getValues()).thenReturn(Arrays.asList("2:45a.m. -7:00 AM", "  9:15AM- 5:30P.M. "));

		RangerTimeOfDayMatcher matcher = new RangerTimeOfDayMatcher();
		matcher.init(null, itemCondition);
		
		Object[][] input = new Object[][] {
				{ 1, 0, false },
				{ 2, 44, false },
				{ 2, 45, true },
				{ 3, 0, true },
				{ 7, 0, true },
				{ 7, 01, false },
				{ 8, 0, false },
				{ 9, 15, true },
				{10, 0, true },
				{17, 0, true}, 
				{17, 30, true}, 
				{17, 31, false}, 
				{18, 0, false },
				{23, 0, false },
		};
		
		RangerAccessRequest request = mock(RangerAccessRequest.class);
		for (Object[] data : input) {
			int hour = (int)data[0];
			int minute = (int)data[1];
			Calendar c = new GregorianCalendar(2015, Calendar.APRIL, 1, hour, minute);
			Date aDate = c.getTime();
			when(request.getAccessTime()).thenReturn(aDate);
			boolean matchExpected = (boolean)data[2];
			if (matchExpected) {
				assertTrue("" + hour, matcher.isMatched(request));
			} else {
				assertFalse("" + hour, matcher.isMatched(request));
			}
		}
	}
}
