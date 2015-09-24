package org.epics.archiverappliance.etl.common;

import org.apache.log4j.Logger;
import org.epics.archiverappliance.common.TimeUtils;
import org.epics.archiverappliance.etl.StorageMetricsContext;

/**
 * ETL metrics for the appliance as a whole for one lifetime
 * @author mshankar
 *
 */
public class ETLMetricsForLifetime {
	private static Logger logger = Logger.getLogger(ETLMetricsForLifetime.class.getName());
	int lifeTimeId;
	StorageMetricsContext metricsContext;
	long totalETLRuns;
	long timeForOverallETLInMilliSeconds;
	
	long startOfMetricsMeasurementInEpochSeconds;
	long timeinMillSecond4appendToETLAppendData;
	long timeinMillSecond4commitETLAppendData;
	long timeinMillSecond4executePostETLTasks;
	long timeinMillSecond4getETLStreams;
	long timeinMillSecond4checkSizes;
	long timeinMillSecond4markForDeletion;
	long timeinMillSecond4prepareForNewPartition;
	long timeinMillSecond4runPostProcessors;
	long totalSrcBytes;
	
	private long approximateLastGlobalETLTimeInMillis = 0;
	private long lastTimeGlobalETLTimeWasUpdatedInEpochSeconds = 0;
	private long[] weeklyETLUsageInMillis = new long[7];
	
	
	public ETLMetricsForLifetime(int lifeTimeId, StorageMetricsContext metricsContext) { 
		this.lifeTimeId = lifeTimeId;
		this.metricsContext = metricsContext;
		this.startOfMetricsMeasurementInEpochSeconds = TimeUtils.getCurrentEpochSeconds();
		for(int i = 0; i < 7; i++) { 
			weeklyETLUsageInMillis[i] = -1;
		}
	}

	public int getLifeTimeId() {
		return lifeTimeId;
	}
	
	public StorageMetricsContext getMetricsContext() {
		return metricsContext;
	}

	public long getTotalETLRuns() {
		return totalETLRuns;
	}

	public long getTimeForOverallETLInMilliSeconds() {
		return timeForOverallETLInMilliSeconds;
	}

	public long getStartOfMetricsMeasurementInEpochSeconds() {
		return startOfMetricsMeasurementInEpochSeconds;
	}

	public long getTimeinMillSecond4appendToETLAppendData() {
		return timeinMillSecond4appendToETLAppendData;
	}

	public long getTimeinMillSecond4commitETLAppendData() {
		return timeinMillSecond4commitETLAppendData;
	}

	public long getTimeinMillSecond4executePostETLTasks() {
		return timeinMillSecond4executePostETLTasks;
	}

	public long getTimeinMillSecond4getETLStreams() {
		return timeinMillSecond4getETLStreams;
	}

	public long getTimeinMillSecond4markForDeletion() {
		return timeinMillSecond4markForDeletion;
	}

	public long getTimeinMillSecond4prepareForNewPartition() {
		return timeinMillSecond4prepareForNewPartition;
	}

	public long getTimeinMillSecond4runPostProcessors() {
		return timeinMillSecond4runPostProcessors;
	}
	
	public long getTimeinMillSecond4checkSizes() {
		return timeinMillSecond4checkSizes;
	}

	public long getTotalSrcBytes() {
		return totalSrcBytes;
	}

	/**
	 * Update the time taken for the last ETL job. Note this is an approximation.
	 * @param lastETLTimeWeSpentInETLInMilliSeconds
	 */
	public void updateApproximateGlobalLastETLTime(long lastETLTimeWeSpentInETLInMilliSeconds) {
		try {
			// This is complex (and inaccurate) because the ETL jobs are done on a per PV basis
			// There is no concept of a global "job" that has a start time and an end time. 
			// Instead, we use a concept of lastUpdate timestamp to make a call on whether to reset the last ETL metric or not.
			// Still unclear if this is actually useful..
			long epochSeconds = System.currentTimeMillis()/1000;
			// We use some kind of cutoff as the boundary between this job and the previous.
			// What this means is that this number is more accurate if the time between one global ETL job and the next is at least this interval.
			// Otherwise this tends to accumulate times from multiple jobs.
			if((epochSeconds - lastTimeGlobalETLTimeWasUpdatedInEpochSeconds) > 15*60) { 
				logger.debug("Resetting approximateLastGlobalETLTimeInMillis for updated for " + this.lifeTimeId);
				approximateLastGlobalETLTimeInMillis = 0;
			}
			lastTimeGlobalETLTimeWasUpdatedInEpochSeconds = epochSeconds;
			approximateLastGlobalETLTimeInMillis += lastETLTimeWeSpentInETLInMilliSeconds;

			// An alternate approach is to determine how much time we spend in ETL over a fixed last time period
			// We choose a week and an days's partition - so we have 7 buckets
			// One of the bucket's (tomorrow's) is almost always 0
			// One of the bucket's (todays's) is almost always incomplete
			long epochDays =  epochSeconds/(24*60*60);
			int dayBucket = (int) (epochDays % 7);
			weeklyETLUsageInMillis[dayBucket] = weeklyETLUsageInMillis[dayBucket] + lastETLTimeWeSpentInETLInMilliSeconds;
			// Reset tomorrow's times..
			int nextDay = (dayBucket + 1) % 7;
			weeklyETLUsageInMillis[nextDay] = -1;
		} catch (Exception ex) { 
			logger.error("Exception updating global ETL times", ex);
		}
	}

	/**
	 * Note this is an approximation and could be inaccurate. 
	 * @return the approximateLastGlobalETLTime
	 */
	public long getApproximateLastGlobalETLTimeInMillis() {
		return approximateLastGlobalETLTimeInMillis;
	}
	
	/**
	 * Get an estimate of how much time (in percent) over the last week we spent performing ETL for this transition.
	 */
	public double getWeeklyETLUsageInPercent() { 
		long epochSeconds = System.currentTimeMillis()/1000;
		long startOfEpochDayInSeconds =  (epochSeconds/(24*60*60))*(24*60*60);
		long secondsIntoDay = epochSeconds - startOfEpochDayInSeconds;
		long totalWeeklyETLMillis = 0;
		long totalDaysInMetric = 0;
		for(long dailyUsageInMillis : weeklyETLUsageInMillis) { 
			if(dailyUsageInMillis != -1) { 
				totalWeeklyETLMillis += dailyUsageInMillis;
				totalDaysInMetric++;
			}
		}
		long totalWeeklyETLSeconds = totalWeeklyETLMillis/1000;
		// See updateApproximateGlobalLastETLTime
		// One of the bucket's (todays's) is almost always incomplete
		totalDaysInMetric = totalDaysInMetric - 1;
		long totalSecondsInMetric = totalDaysInMetric * (24*60*60) + secondsIntoDay;
		logger.debug("totalWeeklyETLSeconds = " + totalWeeklyETLSeconds + " totalSecondsInMetric " + totalSecondsInMetric);
		return (totalWeeklyETLSeconds*100.0)/totalSecondsInMetric;
	}
}
