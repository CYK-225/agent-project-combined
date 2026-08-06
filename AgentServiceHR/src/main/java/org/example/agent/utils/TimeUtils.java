package org.example.agent.utils;

import com.google.common.collect.Maps;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zhoujy
 * @date 创建时间：2017年5月15日 上午11:32:30
 * @see
 */
public class TimeUtils {
    /**
     * yyyy-MM-dd
     */
    public static final DateTimeFormatter DEFAULT_FORMETTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /**
     * yyyy/MM/dd
     */
    public static final DateTimeFormatter FORMETTER_DIAGANAL = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /**
     * yyyyMMdd
     */
    public static final DateTimeFormatter FORMETTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    /**
     * yyyy.MM.dd
     */
    public static final DateTimeFormatter POINT_FORMETTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    /**
     * yyyy.M.d
     */
    public static final DateTimeFormatter POINT_SIMPLE_FORMETTER = DateTimeFormatter.ofPattern("yyyy.M.d");
    /**
     * yyyy.MM.dd HH:mm
     */
    public static final DateTimeFormatter POINT_TIME_FORMETTER_YMDHM = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    /**
     * yyyy.M.d HH:mm
     */
    public static final DateTimeFormatter POINT_TIME_SIMPLE_FORMETTER_YMDHM = DateTimeFormatter.ofPattern("yyyy.M.d HH:mm");
    /**
     * yyyy年MM月dd日
     */
    public static final DateTimeFormatter FORMETTER_CHINESE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    /**
     * yyyy年MM月dd日 HH:mm
     */
    public static final DateTimeFormatter FORMETTER_CHINESE_YMDHM = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    /**
     * MM月dd日 HH:mm
     */
    public static final DateTimeFormatter FORMETTER_CHINESE_MDHM = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
    /**
     * yyyy年MM月
     */
    public static final DateTimeFormatter FORMETTER_CHINESE_YM = DateTimeFormatter.ofPattern("yyyy年MM月");
    /**
     * yyyy-MM-dd HH:mm:ss
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMETTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * yyyyMMddHHmmss
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMETTER_V2 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");


    /**
     * yyyy-MM-dd HH:mm:ss.S
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMETTER_MIlLI = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
    /**
     * yyyy-MM-dd HH:mm
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMETTER_YMDHM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /**
     * HH:mm
     */
    public static final DateFormat dfHM = new SimpleDateFormat("HH:mm");
    /**
     * yyyy-MM-dd
     */
    public static final DateFormat dfYMD = new SimpleDateFormat("yyyy-MM-dd");
    /**
     * HH:mm
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMETTER_HM = DateTimeFormatter.ofPattern("HH:mm");
    /**
     * HH:mm:ss
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMETTER_HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * MM月dd日
     */
    public static final DateTimeFormatter MONTH_DAY_FORMETTER = DateTimeFormatter.ofPattern("MM月dd日");

    /**
     * MM-dd
     */
    public static final DateTimeFormatter MONTH_DAY_LINE_FORMETTER = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * MM.dd
     */
    public static final DateTimeFormatter MONTH_DAY_POINT_FORMETTER = DateTimeFormatter.ofPattern("MM.dd");

    /**
     * MM/dd
     */
    public static final DateTimeFormatter MONTH_DAY_DIAGANAL_FORMETTER = DateTimeFormatter.ofPattern("MM/dd");

    /**
     * yyyy-MM
     */
    public static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * yy年M月
     */
    public static final DateTimeFormatter YEAR_MONTH_CHINESE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月");


    public static final DateTimeFormatter YEAR_MONTH_DAY_CHINESE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");

    /**
     * M月dd
     */
    public static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("M月dd");

    /**
     * M月d日
     */
    public static final DateTimeFormatter SIMPLE_MONTH_DAY_CHINESE_FORMATTER = DateTimeFormatter.ofPattern("M月d日");

    /**
     * M月
     */
    public static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("M月");

    /**
     * yyyy.MM
     */
    public static final DateTimeFormatter POINT_YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM");

    /**
     * M月d日 HH:mm
     */
    public static final DateTimeFormatter MONTH_DAY_HM_FORMATTER = DateTimeFormatter.ofPattern("M月dd日 HH:mm");

    /**
     * yyyy年M月d日 HH:mm
     */
    public static final DateTimeFormatter YEAR_MONTH_DAY_HM_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    /**
     * yyyy/M/d HH:mm:ss
     */
    public static final DateTimeFormatter FORMETTER_YMDHMS = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss");

    /**
     * UNIX时间戳转成LocalDateTime
     *
     * @param unixTime
     * @return
     */
    public static LocalDateTime getTimeByUnixTime(long unixTime) {
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(unixTime / 1000, 0, ZoneOffset.ofHours(8));
        return localDateTime;
    }

    /**
     * 日期格式转换为UNIX时间戳
     *
     * @param dateTime
     * @return
     * @throws ParseException
     */
    public static String getUnixTime(String dateTime) throws ParseException {
        SimpleDateFormat date_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = date_time.parse(dateTime);
        return String.valueOf(date.getTime() / 1000);
    }

    /**
     * 日期格式转换为UNIX时间戳
     * yyyyMMdd
     *
     * @param dateTime
     * @return
     * @throws ParseException
     */
    public static String getUnixDate(String dateTime) throws ParseException {
        SimpleDateFormat date_time = new SimpleDateFormat("yyyyMMdd");
        Date date = date_time.parse(dateTime);
        return String.valueOf(date.getTime() / 1000);
    }


    /**
     * UNIX时间戳转成日期格式
     *
     * @param unixTime
     * @return
     */
    public static String getDateTime(long unixTime) {
        Date date = new Date(unixTime * 1000);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(date);
    }


    /**
     * 返回给定localDateTime,年月日时分秒14位不含符号的时间串
     * 为空则返回当前时间的串
     */
    public static String fullDateStr(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return localDateTime.format(formatter);
    }

    /**
     * 返回给定localDateTime,年月日8位不含符号的时间串
     * 为空则返回当前时间的串
     */
    public static String getyyyyMMdd(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return localDateTime.format(formatter);
    }


    /**
     * 两个时间间隔
     * unit 通过单位来具体获得间隔单位
     *
     * @param firstTime
     * @param secondTime
     * @param unit       @ChronoUnit.SECONDS 秒  @ChronoUnit.MINUTES 分
     * @return
     */
    public static long getTwoTimeInterval(LocalDateTime firstTime,
                                          LocalDateTime secondTime, ChronoUnit unit) {
        return firstTime.until(secondTime, unit);
    }

    /**
     * 获得给定时间所在周，周一0点
     */
    public static LocalDateTime getWeekdayZero(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }
        DayOfWeek df = localDateTime.getDayOfWeek();
        int weekIndex = df.getValue();
        localDateTime = localDateTime.plusDays(-(weekIndex - 1));
        return LocalDateTime.of(localDateTime.getYear(), localDateTime.getMonth(), localDateTime.getDayOfMonth(), 0, 0, 0, 0);
    }

    /**
     * 获得给定时间所在月，月度一号0点
     */
    public static LocalDateTime getMonthdayZero(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }
        return LocalDateTime.of(localDateTime.getYear(), localDateTime.getMonth(), 1, 0, 0, 0, 0);
    }

    /**
     * 将一个整型日期转为MM.dd格式
     */
    public static String getMMddStr(int time, String symbol) {
        String str = time + "";
        if (str.length() < 8) {
            return "";
        }
        return str.substring(4, 6) + symbol + str.substring(6, 8);
    }

    /**
     * 返回给定localDateTime,年月日8位的时间串yyyy-MM-dd
     * 为空则返回当前时间的串
     */
    public static String getyyyy_MM_dd(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }
        return localDateTime.format(DEFAULT_FORMETTER);
    }

    /**
     * 返回完整的时间字符串
     */
    public static String getFullStrTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return localDateTime.format(formatter);
    }

    /**
     * 返回中文中的周几
     *
     * @param weekValue
     * @return
     */
    public static String getChineseWeekName(int weekValue) {
        if (weekValue > 7) {
            weekValue -= 7;
        }
        if (weekValue <= 0) {
            weekValue = 1;
        }
        return weekValue == 1 ? "周一" : (weekValue == 2 ? "周二" : (weekValue == 3 ? "周三" : (weekValue == 4 ? "周四" : (weekValue == 5 ? "周五" : (weekValue == 6 ? "周六" : "周日")))));
    }

    /**
     * 返回中文中的星期几
     *
     * @param weekValue
     * @return
     */
    public static String getChineseWeekName2(int weekValue) {
        if (weekValue > 7) {
            weekValue -= 7;
        }
        if (weekValue <= 0) {
            weekValue = 1;
        }
        return weekValue == 1 ? "星期一" : (weekValue == 2 ? "星期二" : (weekValue == 3 ? "星期三" : (weekValue == 4 ? "星期四" : (weekValue == 5 ? "星期五" : (weekValue == 6 ? "星期六" : "星期日")))));
    }

    /**
     * 返回今日、明日、后天
     *
     * @param weekValue
     * @return
     */
    public static String getSpecialWeekName(int weekValue) {
        LocalDateTime now = LocalDateTime.now();
        int dayOfWeek = now.getDayOfWeek().getValue();
        return weekValue == dayOfWeek ? "今日" : (weekValue == dayOfWeek + 1 ? "明日" : "后天");
    }

    /**
     * 返回一周7天的日期
     *
     * @param plusWeek 本周传0
     * @return
     */
    public static List<String> getWeekDateList(int plusWeek) {
        LocalDateTime nowTime = LocalDateTime.now();
        LocalDateTime startTime = nowTime.plusWeeks(plusWeek).minusDays(nowTime.getDayOfWeek().getValue() - 1);
        List<String> dateList = new ArrayList<String>();
        for (int i = 0; i < 7; i++) {
            dateList.add(startTime.plusDays(i).format(DEFAULT_FORMETTER));
        }
        return dateList;
    }

    /**
     * 返回一周7天的日期
     *
     * @param plusWeek 本周传0
     * @return
     */
    public static List<String> getWeekDateList(int plusWeek, boolean sone) {
        LocalDate nowDate = LocalDate.now();
        LocalDate beginDate = nowDate.plusWeeks(plusWeek).minusDays(nowDate.getDayOfWeek().getValue() - 1);
        if (sone) {
            beginDate = nowDate.plusWeeks(plusWeek).minusDays(nowDate.getDayOfWeek().getValue() % 7);
        }
        List<String> dateList = new ArrayList<String>();
        for (int i = 0; i < 7; i++) {
            dateList.add(beginDate.plusDays(i).format(DEFAULT_FORMETTER));
        }
        return dateList;
    }

    /**
     * 返回给定日期的一周
     *
     * @return
     */
    public static List<String> getWeekDateList(String dateStr, String pattern) {
        LocalDateTime atStartOfDay = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
        LocalDateTime startTime = atStartOfDay.plusWeeks(0L).minusDays(atStartOfDay.getDayOfWeek().getValue() - 1);
        List<String> dateList = new ArrayList<String>();
        for (int i = 0; i < 7; i++) {
            dateList.add(startTime.plusDays(i).format(DEFAULT_FORMETTER));
        }
        return dateList;
    }

    /**
     * 返回给定日期的一周
     *
     * @return
     */
    public static List<String> getWeekDateList(String dateStr, String pattern, boolean sone) {
        LocalDateTime atStartOfDay = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
        LocalDateTime startTime = atStartOfDay.plusWeeks(0L).minusDays(atStartOfDay.getDayOfWeek().getValue() - 1);
        if (sone) {
            startTime = atStartOfDay.plusWeeks(0L).minusDays(atStartOfDay.getDayOfWeek().getValue() % 7);
        }
        List<String> dateList = new ArrayList<String>();
        for (int i = 0; i < 7; i++) {
            dateList.add(startTime.plusDays(i).format(DEFAULT_FORMETTER));
        }
        return dateList;
    }

    public static String format(String date, String inPattern, String outPattern) {
        LocalDateTime localDateTime = LocalDate.parse(date, DateTimeFormatter.ofPattern(inPattern)).atStartOfDay();
        return localDateTime.format(DateTimeFormatter.ofPattern(outPattern));
    }

    /**
     * 获取指定日期所在月的第一天
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getFirstDayWithMonth(LocalDate nowDate) {
        LocalDate firstDay = nowDate.minusDays(nowDate.getDayOfMonth() - 1);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /***
     * 获取指定日期所在年的第一天
     * @param nowDate
     * @return
     */
    public static String getFirstDayWithYear(LocalDate nowDate) {
        LocalDate firstDay = nowDate.minusDays(nowDate.getDayOfYear() - 1);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在月的最后一天
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getLastDayWithMonth(LocalDate nowDate) {
        LocalDate nextMonth = nowDate.plusMonths(1);
        LocalDate lastDay = nextMonth.minusDays(nextMonth.getDayOfMonth());
        return lastDay.format(DEFAULT_FORMETTER);
    }

    public static String getLastDayWithYear(LocalDate nowDate) {
        LocalDate nextYear = nowDate.plusYears(1);
        LocalDate lastDay = nextYear.minusDays(nextYear.getDayOfYear());
        return lastDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在周的第一天
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getFirstDayWithWeek(LocalDate nowDate) {
        LocalDate firstDay = nowDate.minusDays(nowDate.getDayOfWeek().getValue() - 1);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在月的第一个周一
     */
    public static String getFirstWeekDayWithMonth(LocalDate nowDate) {
        //当月第一天
        LocalDate firstDayOfMonth = nowDate.with(TemporalAdjusters.firstDayOfMonth());
        // 获取该周的周一
        LocalDate firstDay = firstDayOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在月的第最后一个周日
     */
    public static String getLastWeekDayWithMonth(LocalDate nowDate) {
        //当月最后一天
        LocalDate lastDayOfMonth = nowDate.with(TemporalAdjusters.lastDayOfMonth());
        // 获取下一个周日（可能在下个月）
        LocalDate nextSunday = lastDayOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return nextSunday.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在周的最后一天
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getLastDayWithWeek(LocalDate nowDate) {
        LocalDate lastDay = nowDate.plusWeeks(1).minusDays(nowDate.getDayOfWeek().getValue());
        return lastDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在周的第一天
     *
     * @param sone 是否以周日作为一周的开始
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getFirstDayWithWeek(LocalDate nowDate, boolean sone) {
        if (sone) {
            LocalDate firstDay = nowDate.minusDays(nowDate.getDayOfWeek().getValue() % 7);
            return firstDay.format(DEFAULT_FORMETTER);
        } else {
            return getFirstDayWithWeek(nowDate);
        }
    }

    /**
     * 获取指定日期所在周的最后一天
     *
     * @param sone 是否以周日作为一周的开始
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getLastDayWithWeek(LocalDate nowDate, boolean sone) {
        if (sone) {
            LocalDate lastDay = nowDate.plusWeeks(1).minusDays(nowDate.getDayOfWeek().getValue() % 7 + 1);
            return lastDay.format(DEFAULT_FORMETTER);
        } else {
            return getLastDayWithWeek(nowDate);
        }

    }

    /**
     * 获取本月第一天
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getFirstDayWithMonth() {
        LocalDate nowDate = LocalDate.now();
        LocalDate firstDay = nowDate.minusDays(LocalDate.now().getDayOfMonth() - 1);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取本月最后一天
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static String getLastDayWithMonth() {
        LocalDate nowDate = LocalDate.now();
        LocalDate nextMonth = nowDate.plusMonths(1);
        LocalDate lastDay = nextMonth.minusDays(nextMonth.getDayOfMonth());
        return lastDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取本月最后一天数值
     *
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:14
     * Description
     */
    public static int getLastDayValueWithMonth() {
        LocalDate nowDate = LocalDate.now();
        LocalDate nextMonth = nowDate.plusMonths(1);
        LocalDate lastDay = nextMonth.minusDays(nextMonth.getDayOfMonth());
        return lastDay.getDayOfMonth();
    }

    /**
     * 获取前x月的第一天
     *
     * @param monthsToSubtract
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:54
     * Description
     */
    public static String getFirstDayWithMonth(long monthsToSubtract) {
        LocalDate nowDate = LocalDate.now().minusMonths(monthsToSubtract);
        ;
        LocalDate firstDay = nowDate.minusDays(LocalDate.now().getDayOfMonth() - 1);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取前x月的最后一天
     *
     * @param monthsToSubtract
     * @return
     * @author Vision
     * 2017年8月12日 下午9:17:54
     * Description
     */
    public static String getLastDayWithMonth(long monthsToSubtract) {
        LocalDate nowDate = LocalDate.now().minusMonths(monthsToSubtract);
        LocalDate nextMonth = nowDate.plusMonths(1);
        LocalDate lastDay = nextMonth.minusDays(nextMonth.getDayOfMonth());
        return lastDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 日期字符串格式转换
     *
     * @param patternNow
     * @param pattermResult
     * @param date
     * @return 2017年8月11日 上午10:25:22
     * Description
     * @author Vision
     */
    public static String dateStringConvert(String patternNow, String pattermResult, String date) {
        try {
            DateTimeFormatter formatterNow = DateTimeFormatter.ofPattern(patternNow);
            DateTimeFormatter formatteResult = DateTimeFormatter.ofPattern(pattermResult);
            return LocalDate.parse(date, formatterNow).format(formatteResult);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getDateStrListWithDay(String beginStr, String endStr) {
        try {
            LocalDate beginDate = LocalDate.parse(beginStr, DEFAULT_FORMETTER);
            LocalDate endDate = LocalDate.parse(endStr, DEFAULT_FORMETTER);
            List<String> dateList = new ArrayList<String>();
            long days = beginDate.until(endDate, ChronoUnit.DAYS);
            for (int i = 0; i <= days; i++) {
                dateList.add(beginDate.plusDays(i).format(DEFAULT_FORMETTER));
            }
            return dateList;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getDateStrListWithWeek(String beginStr,
                                                      String endStr) {
        try {
            LocalDate beginDate = LocalDate.parse(beginStr, DEFAULT_FORMETTER);
            LocalDate endDate = LocalDate.parse(endStr, DEFAULT_FORMETTER);
            List<String> dateList = new ArrayList<String>();
            int year = endDate.getYear();
            int week = endDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);//1
            int i = 0;
            while (!(beginDate.getYear() == year && beginDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == week)) {
                beginDate = beginDate.plusWeeks(i);
                /**
                 * fix bugs
                 * 解决跨年导致break条件失效
                 */
                if (endDate.isBefore(beginDate)) {
                    break;
                }
                dateList.add(beginDate.format(DEFAULT_FORMETTER));
                if (i == 0) {
                    i++;
                }
            }
            return dateList;
        } catch (Exception e) {
            return null;
        }
    }


    public static List<String> getDateStrListWithMonth(String beginStr,
                                                       String endStr) {
        try {
            LocalDate beginDate = LocalDate.parse(beginStr, DEFAULT_FORMETTER);
            LocalDate endDate = LocalDate.parse(endStr, DEFAULT_FORMETTER);
            List<String> dateList = new ArrayList<String>();
            int days = endDate.get(ChronoField.MONTH_OF_YEAR) + ((endDate.getYear() - beginDate.getYear()) * 12) - beginDate.get(ChronoField.MONTH_OF_YEAR);
            for (int i = 0; i <= days; i++) {
                dateList.add(beginDate.plusMonths(i).format(DEFAULT_FORMETTER));
            }
            return dateList;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getMonthStrListWithMonth(String beginStr,
                                                        String endStr) {
        try {
            LocalDate beginDate = LocalDate.parse(beginStr, DEFAULT_FORMETTER);
            LocalDate endDate = LocalDate.parse(endStr, DEFAULT_FORMETTER);
            List<String> dateList = new ArrayList<String>();
            int days = endDate.get(ChronoField.MONTH_OF_YEAR) + ((endDate.getYear() - beginDate.getYear()) * 12) - beginDate.get(ChronoField.MONTH_OF_YEAR);
            for (int i = 0; i <= days; i++) {
                dateList.add(beginDate.plusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM")));
            }
            return dateList;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isNowDay(LocalDate date) {
        return LocalDate.now().isEqual(date);
    }

    public static boolean isNowWeek(LocalDate date) {
        LocalDate now = LocalDate.now();
        return (now.getYear() == date.getYear() && now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }


    public static boolean isNowMonth(LocalDate date) {
        ;
        LocalDate now = LocalDate.now();
        return (now.getYear() == date.getYear() && now.getMonthValue() == date.getMonthValue());
    }

    /**
     * 返回布尔型 判断date是否在beginDate与endDate周期内
     *
     * @param beginDate
     * @param endDate
     * @param date
     * @return 2017年8月25日 上午11:42:54
     * Description
     * @author Vision
     */
    public static boolean isBetween(String beginDate, String endDate,
                                    LocalDate date) {
        LocalDate begin = LocalDate.parse(beginDate, DEFAULT_FORMETTER);
        LocalDate end = LocalDate.parse(endDate, DEFAULT_FORMETTER);
        return (date.isAfter(begin) && date.isBefore(end)) || date.equals(begin) || date.isEqual(end);
    }

    /**
     * 返回布尔型 判断time是否在beginDateTime与endDateTime周期内
     *
     * @return Description
     */
    public static boolean isBetween(LocalDateTime beginTime, LocalDateTime endTime,
                                    LocalDateTime time) {
        // 去除毫秒的干扰
        beginTime = beginTime.withNano(0);
        endTime = endTime.withNano(0);
        time = time.withNano(0);
        return (time.isAfter(beginTime) && time.isBefore(endTime)) || time.equals(beginTime) || time.isEqual(endTime);
    }

    /**
     * 获取从上X周开始 最近Y周的 日期list数据
     *
     * @param startWeeks
     * @param weeksToSubtract
     * @return
     */
    public static Map<Integer, List<String>> getDateListOfRecentlyWeek(long startWeeks, long weeksToSubtract) {
        //开始时间
        LocalDate startDate = LocalDate.now().minusWeeks(weeksToSubtract);
        LocalDate endDate = LocalDate.now().minusWeeks(startWeeks);
        //拿到开始天所在的周的第一天
        String firstDayWithWeek = getFirstDayWithWeek(startDate);
        //拿到结束天所在的周的最后一天
        String lastDayWithWeek = getLastDayWithWeek(endDate);
        //获取开始时间和结束时间区间的日期集合
        List<String> dateStrListWithWeek = getDateStrListWithDay(firstDayWithWeek, lastDayWithWeek);
        int j = 1;
        Map<Integer, List<String>> resultDateListMap = new LinkedHashMap<>();
        for (int i = 0; i < dateStrListWithWeek.size(); i++) {
            String lastDayOfWeek = getLastDayWithWeek(LocalDate.parse(dateStrListWithWeek.get(i), DEFAULT_FORMETTER));
            //获取该周的日期list集合
            List<String> dataList = getDateStrListWithDay(dateStrListWithWeek.get(i), lastDayOfWeek);
            i += dataList.size() - 1;
            if (i < dateStrListWithWeek.size()) {
                resultDateListMap.put(j, dataList);
                j++;
            } else {
                resultDateListMap.put(j, dateStrListWithWeek.subList(i + 1 - dataList.size(), dateStrListWithWeek.size()));
            }
        }
        return resultDateListMap;
    }

    /**
     * 获取从上X月开始 最近Y月的 日期list数据
     *
     * @param startMonths
     * @param monthsTosubtract
     * @return
     */
    public static Map<Integer, List<String>> getDateListOfRecentlyMonth(long startMonths, long monthsTosubtract) {
        //开始时间
        LocalDate startDate = LocalDate.now().minusMonths(monthsTosubtract);
        LocalDate endDate = LocalDate.now().minusMonths(startMonths);
        //拿到开始天所在的周的第一天
        String firstDayWithMonth = getFirstDayWithMonth(startDate);
        //拿到结束天所在的周的最后一天
        String lastDayWithMonth = getLastDayWithMonth(endDate);
        //获取开始时间和结束时间区间的日期集合
        List<String> dateStrListWithMonth = getDateStrListWithDay(firstDayWithMonth, lastDayWithMonth);
        int j = 1;
        Map<Integer, List<String>> resultDateListMap = new LinkedHashMap<>();
        for (int i = 0; i < dateStrListWithMonth.size(); i++) {
            String lastDayOfMonth = getLastDayWithMonth(LocalDate.parse(dateStrListWithMonth.get(i), DEFAULT_FORMETTER));
            //获取该周的日期list集合
            List<String> dataList = getDateStrListWithDay(dateStrListWithMonth.get(i), lastDayOfMonth);
            i += dataList.size() - 1;
            if (i < dateStrListWithMonth.size()) {
                resultDateListMap.put(j, dataList);
                j++;
            } else {
                resultDateListMap.put(j, dateStrListWithMonth.subList(i + 1 - dataList.size(), dateStrListWithMonth.size()));
            }
        }
        return resultDateListMap;
    }


    /**
     * 获取前x月 按周分组的datelist
     *
     * @param monthsToSubtract
     * @return
     */
    public static Map<Integer, List<String>> getDateListGroupByWeek(long monthsToSubtract) {
        LocalDate nowDate = LocalDate.now().minusMonths(monthsToSubtract);
        LocalDate firstDay = nowDate.minusDays(LocalDate.now().getDayOfMonth() - 1);
        LocalDate nextMonth = nowDate.plusMonths(1);
        LocalDate lastDay = nextMonth.minusDays(nextMonth.getDayOfMonth());
        //该月的开始时间 结束时间
        String beginUseDate = firstDay.format(DEFAULT_FORMETTER);
        String endUseDate = lastDay.format(DEFAULT_FORMETTER);
        List<String> dateStrListWithDay = getDateStrListWithDay(beginUseDate, endUseDate);
        int j = 1;
        Map<Integer, List<String>> resultDateListMap = new HashMap<>();
        for (int i = 0; i < dateStrListWithDay.size(); i++) {
            String lastDayWithWeek = getLastDayWithWeek(LocalDate.parse(dateStrListWithDay.get(i), DEFAULT_FORMETTER));
            List<String> dataList = getDateStrListWithDay(dateStrListWithDay.get(i), lastDayWithWeek);
            i += dataList.size() - 1;
            if (i < dateStrListWithDay.size()) {
                resultDateListMap.put(j, dataList);
                j++;
            } else {
                resultDateListMap.put(j, dateStrListWithDay.subList(i + 1 - dataList.size(), dateStrListWithDay.size()));
            }
        }
        return resultDateListMap;
    }

    /**
     * 获取最近前X月  按月分组获取每月的日期集合
     *
     * @param monthsToSubtract
     * @return
     */
    public static Map<String, List<String>> getDateListGroupByMonth(long monthsToSubtract) {
        Map<String, List<String>> resultDateListMap = new HashMap<>();
        for (int i = 0; i < monthsToSubtract; i++) {
            String firstDayWithMonth = getFirstDayWithMonth(i);
            String lastDayWithMonth = getLastDayWithMonth(i);
            List<String> dateStrListWithDay = getDateStrListWithDay(firstDayWithMonth, lastDayWithMonth);
            resultDateListMap.put(firstDayWithMonth, dateStrListWithDay);
        }
        return resultDateListMap;
    }

    /**
     * 获取历史查询日期集合
     * 主要是在查询7天之前历史统计表时会用到，将时间段传进来可以返回该时间段内需要历史数据表查询的时间集合
     *
     * @param beginDate
     * @param endDate
     * @return
     */
    public static List<String> getHistoryQueryDateList(String beginDate, String endDate) {
        List<String> dateList = null;
        try {
            LocalDate historyDate = LocalDate.now().minusDays(8);
            LocalDate beginDateLocalDate = LocalDate.parse(beginDate);
            LocalDate endDateLocalDate = LocalDate.parse(endDate);
            if (endDateLocalDate.isAfter(historyDate)) {
                endDateLocalDate = historyDate;
            }
            beginDate = beginDateLocalDate.format(DEFAULT_FORMETTER);
            endDate = endDateLocalDate.format(DEFAULT_FORMETTER);
            dateList = getDateStrListWithDay(beginDate, endDate);
        } catch (Exception e) {
            dateList = Collections.emptyList();
        }
        return dateList;
    }

    /**
     * 获取实时查询日期集合
     * 主要是在查询一周以内的数据时会使用到
     *
     * @param beginDate
     * @param endDate
     * @return
     */
    public static List<String> getCurrentQueryDateList(String beginDate, String endDate) {
        List<String> dateList = null;
        try {
            LocalDate curWeekDate = LocalDate.now().minusDays(7);
            LocalDate beginDateLocalDate = LocalDate.parse(beginDate);
            LocalDate endDateLocalDate = LocalDate.parse(endDate);
            if (beginDateLocalDate.isBefore(curWeekDate)) {
                beginDateLocalDate = curWeekDate;
            }
            beginDate = beginDateLocalDate.format(DEFAULT_FORMETTER);
            endDate = endDateLocalDate.format(DEFAULT_FORMETTER);
            dateList = getDateStrListWithDay(beginDate, endDate);
        } catch (Exception e) {
            dateList = Collections.emptyList();
        }
        return dateList;
    }

    /**
     * 是否为工作日(周一到周五)
     *
     * @param date
     * @return
     */
    public static boolean isWorkDay(String date) {
        try {
            LocalDate localDate = LocalDate.parse(date, DEFAULT_FORMETTER);
            if (localDate.getDayOfWeek().getValue() <= 5) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean isWorkDayOneRest(String date) {
        try {
            LocalDate localDate = LocalDate.parse(date, DEFAULT_FORMETTER);
            if (localDate.getDayOfWeek().getValue() <= 6) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }


    /**
     * 判断时间是否在本月之内
     */
    public static boolean isInThisMonth(LocalDateTime time) {
        LocalDate localDate = time.toLocalDate();
        LocalDate now = LocalDate.now();
        return localDate.isAfter(now.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth())) &&
                localDate.isBefore(now.plusMonths(1).with(TemporalAdjusters.firstDayOfMonth()));
    }

    /**
     * 判断 startTime 是否大于= endTime
     * 比较日期大小 默认小时级别
     *
     * @param df      DateFormat df = new SimpleDateFormat("HH:mm");
     * @param endTime
     * @return
     */
    public static boolean compareTime(DateFormat df, String startTime, String endTime) {
        if (df == null) {
            df = dfHM;
        }
        try {
            Date dt1 = df.parse(startTime);//将字符串转换为date类型
            Date dt2 = df.parse(endTime);
            if (dt1.getTime() >= dt2.getTime())//比较时间大小,如果dt1大于dt2
            {
                return true;
            } else {
                return false;
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 判断 startTime 是否大于 endTime
     * 比较日期大小 默认小时级别
     *
     * @param df      DateFormat df = new SimpleDateFormat("HH:mm");
     * @param endTime
     * @return
     */
    public static boolean compareBeTime(DateFormat df, String startTime, String endTime) {
        if (df == null) {
            df = dfHM;
        }
        try {
            Date dt1 = df.parse(startTime);//将字符串转换为date类型
            Date dt2 = df.parse(endTime);
            if (dt1.getTime() > dt2.getTime())//比较时间大小,如果dt1大于dt2
            {
                return true;
            } else {
                return false;
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 格式转换
     * 例如 2020-11-25 转成 2020年11月25日
     *
     * @param localDateStr
     * @param needYear     是否需要年
     * @return
     */
    public static String formatConversion(String localDateStr, boolean needYear) {
        LocalDate localDate = LocalDate.parse(localDateStr, DEFAULT_FORMETTER);
        DateTimeFormatter dateTimeFormatter;
        if (needYear) {
            dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
        } else {
            dateTimeFormatter = DateTimeFormatter.ofPattern("MM月dd日");
        }
        return localDate.format(dateTimeFormatter);

    }

    /**
     * 检测时间传入时间是否 按大小排序 小->大
     *
     * @param time
     * @return
     */
    public static boolean timeSizeSort(List<String> time) {
        String flgTime = null;
        for (String s : time) {
            if (flgTime != null) {
                boolean b = compareTime(dfHM, flgTime, s);
                if (b) {
                    //第一个大于第二个
                    return false;
                } else {
                    flgTime = s;
                }
            } else {
                flgTime = s;
            }
        }
        return true;
    }

    public static void main(String[] args) {

        Random re = new Random();
        int i = re.nextInt(9);
        System.out.println(i);
        List<String> a = new ArrayList<>();
        a.add("00:00");
        a.add("00:01");
        a.add("01:00");
        a.add("01:00");
        a.add("02:00");
        boolean b = timeSizeSort(a);
        System.out.println(b);
//		getDateListGroupByMonth(12);
        getDateStrListWithWeek("2019-12-30", "2020-01-05");
//		getDateStrListWithWeek("2019-12-02","2019-12-29");
//		Map<Integer, List<String>> dateListOfRecentlyWeek = getDateListOfRecentlyWeek(1, 12);
//		Map<Integer, List<String>> dateListOfRecentlyMonth = getDateListOfRecentlyMonth(1, 12);
    }

    // 给定的时间在本周内
    public static boolean inThisWeek(LocalDateTime localDateTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekdayZero = getWeekdayZero(now);
        LocalDateTime dateTime = weekdayZero.plusDays(7);
        if (localDateTime.equals(weekdayZero) || (localDateTime.isAfter(weekdayZero) && localDateTime.isBefore(dateTime))) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * 根据当前是否大周 来 计算判断指定日期是否是大周
     *
     * @param isBigWeek 当前日期是否定义为大周 true=大周, false=小周
     * @param date      需要计算判断的指定日期
     * @return
     */
    public static Boolean isBigWeek(boolean isBigWeek, LocalDate date) {
        if (date != null) {
            // 获取当前周的周日
            LocalDate nowDate = LocalDate.now();
            LocalDate currentWeekLastDay = nowDate.plusWeeks(1).minusDays(nowDate.getDayOfWeek().getValue());

            LocalDate lastDay = date.plusWeeks(1).minusDays(date.getDayOfWeek().getValue());

            long days = currentWeekLastDay.until(lastDay, ChronoUnit.DAYS);
            if (days / 7 % 2 != 0) {
                return !isBigWeek;
            } else {
                return isBigWeek;
            }
        }
        return null;
    }

    /**
     * 获取当前时间对应的默认餐段
     * 餐段默认时间段分：
     * 早1    00:00:00 - 09:30:00
     * 午2    09:30:00 - 14:30:00
     * 茶5    14:30:00 - 15:45:00
     * 晚3    15:45:00 - 20:30:00
     * 宵4    20:30:00 - 00:00:00
     *
     * @return
     */
    public static int getDefaultIntervalNo() {
        int intervalNoIndex = 0;
        LocalDateTime nowTime = LocalDateTime.now();
        String nowDateStr = nowTime.format(DEFAULT_FORMETTER);
        if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 09:30:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 1;
        } else if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 14:30:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 2;
        } else if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 15:45:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 5;
        } else if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 20:30:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 3;
        } else {
            intervalNoIndex = 4;
        }
        return intervalNoIndex;
    }

    /**
     * 获取当前时间对应的餐段
     * 工作台定制：
     * 餐段时间段分：
     * 早1    06:00:00 - 09:00:00
     * 午2    09:00:00 - 12:00:00
     * 茶5    12:00:00 - 15:00:00
     * 晚3    15:00:00 - 18:00:00
     * 宵4    18:00:00 - 06:00:00
     *
     * @return
     */
    public static int getCustomIntervalNo() {
        int intervalNoIndex = 0;
        LocalDateTime nowTime = LocalDateTime.now();
        String nowDateStr = nowTime.format(DEFAULT_FORMETTER);
        if (nowTime.isAfter(LocalDateTime.parse(nowDateStr + " 06:00:00", DEFAULT_TIME_FORMETTER)) && nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 09:00:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 1;
        } else if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 12:00:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 2;
        } else if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 15:00:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 5;
        } else if (nowTime.isBefore(LocalDateTime.parse(nowDateStr + " 18:00:00", DEFAULT_TIME_FORMETTER))) {
            intervalNoIndex = 3;
        } else {
            intervalNoIndex = 4;
        }
        return intervalNoIndex;
    }

    /**
     * 获取当天所剩秒数
     */
    public static Integer getRemainSecondsCurrentDay() {
        Date currentDate = new Date();
        //使用plusDays加传入的时间加1天，将时分秒设置成0
        LocalDateTime midnight = LocalDateTime.ofInstant(currentDate.toInstant(),
                        ZoneId.systemDefault()).plusDays(1).withHour(0).withMinute(0)
                .withSecond(0).withNano(0);
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(currentDate.toInstant(),
                ZoneId.systemDefault());
        //使用ChronoUnit.SECONDS.between方法，传入两个LocalDateTime对象即可得到相差的秒数
        long seconds = ChronoUnit.SECONDS.between(currentDateTime, midnight);
        return (int) seconds;
    }

    /**
     * 获取今天星期几
     *
     * @return
     */
    public static Integer getTodayDayOfWeekValue() {
        return LocalDate.now().getDayOfWeek().getValue();
    }

    public static boolean isSaturday() {
        return LocalDate.now().getDayOfWeek().getValue() == 6;
    }

    /**
     * 获取指定日期所在周的第几天
     */
    public static String getFixedDayWithWeek(LocalDate nowDate, FixedDay fixedDay) {
        int days = 0;
        switch (fixedDay) {
            case MON: {
                days = 1;
                break;
            }
            case TUES: {
                days = 2;
                break;
            }
            case WED: {
                days = 3;
                break;
            }
            case THUR: {
                days = 4;
                break;
            }
            case FRI: {
                days = 5;
                break;
            }
            case SAT: {
                days = 6;
                break;
            }
            case SUN: {
                days = 7;
                break;
            }
        }
        LocalDate firstDay = nowDate.minusDays(nowDate.getDayOfWeek().getValue() - days);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在周的第几天
     */
    public static String getFixedDayWithWeek(LocalDate nowDate, int days) {
        if (days < 0) {
            days = 0;
        }
        if (days > 7) {
            days = 7;
        }
        LocalDate firstDay = nowDate.minusDays(nowDate.getDayOfWeek().getValue() - days);
        return firstDay.format(DEFAULT_FORMETTER);
    }

    /**
     * 获取指定日期所在月的第几天
     */
    public static String getFixedDayWithMonth(LocalDate nowDate, int days) {
        try {
            return nowDate.withDayOfMonth(days).format(DEFAULT_FORMETTER);
        } catch (Exception e) {
            e.printStackTrace();
            // 返回最后一天
            return nowDate.withDayOfMonth(nowDate.lengthOfMonth()).format(DEFAULT_FORMETTER);
        }
    }

    /**
     * 获取指定日期所在月的第几天
     */
    public static LocalDate getFixedDateWithMonth(LocalDate nowDate, int days) {
        try {
            return nowDate.withDayOfMonth(days);
        } catch (Exception e) {
            e.printStackTrace();
            // 返回最后一天
            return nowDate.withDayOfMonth(nowDate.lengthOfMonth());
        }
    }

    /**
     * 获取两个日期相差的星期数
     */
    public static long getWeeksByDate(LocalDate baseDate, LocalDate compareDate) {
        // 转换成周一比较，避免周日比较问题
        long weeks = baseDate.minusDays(baseDate.getDayOfWeek().getValue() - 1).until(compareDate.minusDays(compareDate.getDayOfWeek().getValue() - 1), ChronoUnit.WEEKS);
        return weeks >= 0 ? weeks : -weeks;
    }

    /**
     * 获取两个日期相差的月数
     */
    public static long getMonthsByDate(LocalDate baseDate, LocalDate compareDate) {
        // 转换成1号比较
        long months = baseDate.minusDays(baseDate.getDayOfMonth() - 1).until(compareDate.minusDays(compareDate.getDayOfMonth() - 1), ChronoUnit.MONTHS);
        return months >= 0 ? months : -months;
    }

    /**
     * 计算逾期日期date1 在 date2 几天前，date1 : 2025-05-01，date2: 2025-05-04，返回3；date2:2025-04-20，返回0
     */
    public static Integer getOverdueDay(String date1Str, String date2Str) {

        LocalDate date1 = LocalDate.parse(date1Str, DEFAULT_FORMETTER);
        LocalDate date2 = LocalDate.parse(date2Str, DEFAULT_FORMETTER);

        if (date1.isAfter(date2)) {
            return 0;
        }
        long until = date1.until(date2, ChronoUnit.DAYS);

        return Math.toIntExact(until);
    }

    public enum FixedDay {
        MON, TUES, WED, THUR, FRI, SAT, SUN;
    }

    /**
     * 时间戳转换成日期
     *
     * @param timestamp
     * @return
     */
    public static String timestampConvertToDate(long timestamp) {
        return dfYMD.format(new Date(timestamp));
    }


    public static Map<String, String> splitDate(String startDateStr, String endDateStr, String splitDateStr) {
        return splitDate(startDateStr, endDateStr, splitDateStr, DEFAULT_FORMETTER);
    }

//重载华哥的split date

    /**
     * 将日期范围根据某天拆分两段日期范围
     *
     * @param startDateStr 开始日期
     * @param endDateStr   结束日期
     * @param splitDateStr 拆分日期节点
     * @param formatter    用于解析日期的 DateTimeFormatter
     *                     <p>
     *                     如：2023-11-01 与  2023-11-13 按 2023-11-05 拆分
     *                     结果：2023-11-01、2023-11-05、2023-11-06、2023-11-13
     *                     <p>
     *                     如：2023-11-01 与  2023-11-13 按 2023-10-30 拆分
     *                     结果：null、2023-10-30、2023-11-01、2023-11-13
     *                     <p>
     *                     如：2023-11-01 与  2023-11-13 按 2023-11-15 拆分
     *                     结果：2023-11-01、2023-11-13、2023-11-15、null
     * @return 返回  "historyStartDate"、"historyEndDate"、"curStartDate"、"curEndDate"为key的map
     */
    public static Map<String, String> splitDate(String startDateStr, String endDateStr, String splitDateStr, DateTimeFormatter formatter) {
        LocalDate startDate = LocalDate.parse(startDateStr, formatter);
        LocalDate endDate = LocalDate.parse(endDateStr, formatter);
        LocalDate splitDate = LocalDate.parse(splitDateStr, formatter);
        Map<String, LocalDate> stringLocalDateMap = splitDate(startDate, endDate, splitDate);
        Map<String, String> res = Maps.newHashMapWithExpectedSize(4);
        stringLocalDateMap.forEach((k, v) -> res.put(k, v == null ? null : formatter.format(v)));
        return res;
    }


    /**
     * 将日期范围根据某天拆分两段日期范围
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param splitDate 拆分日期节点
     *                  <p>
     *                  如：2023-11-01 与  2023-11-13 按 2023-11-05 拆分
     *                  结果：2023-11-01、2023-11-05、2023-11-06、2023-11-13
     *                  <p>
     *                  如：2023-11-01 与  2023-11-13 按 2023-10-30 拆分
     *                  结果：null、2023-10-30、2023-11-01、2023-11-13
     *                  <p>
     *                  如：2023-11-01 与  2023-11-13 按 2023-11-15 拆分
     *                  结果：2023-11-01、2023-11-13、2023-11-15、null
     */
    public static Map<String, LocalDate> splitDate(LocalDate startDate, LocalDate endDate, LocalDate splitDate) {
        Map<String, LocalDate> result = new HashMap<>();

        // 实时统计基础开始时间
        LocalDate curStartDate = splitDate.plusDays(1);
        // 实时统计基础结束时间，待判断
        LocalDate curEndDate = null;
        // 历史统计基础开始时间，待判断
        LocalDate historyStartDate = null;
        // 历史统计基础结束时间
        LocalDate historyEndDate = splitDate;
        // 如果查询的开始时间比实时统计的基础开始时间晚，那么实时统计的基础开始时间变为查询的开始时间，实时统计的基础结束时间变为查询的结束时间
        if (!startDate.isBefore(curStartDate)) {
            curStartDate = startDate;
            curEndDate = endDate;
        } else {
            // 查询的开始时间比实时统计的基础开始时间要早，那么历史统计的基础开始时间变为查询的开始时间
            historyStartDate = startDate;
            // 如果查询的结束时间比历史统计的基础结束时间要晚，那么实时统计的基础结束时间变为查询的结束时间
            if (endDate.isAfter(historyEndDate)) {
                curEndDate = endDate;
            } else {
                historyEndDate = endDate;
            }
        }

        result.put("historyStartDate", historyStartDate);
        result.put("historyEndDate", historyEndDate);
        result.put("curStartDate", curStartDate);
        result.put("curEndDate", curEndDate);
        return result;
    }

    /**
     * 获取上个月第一天
     */
    public static LocalDate getLastMonthFirstDay() {
        LocalDate now = LocalDate.now();
        return now.minusMonths(1).withDayOfMonth(1);
    }

    /**
     * 获取上个月第一天
     */
    public static String getLastMonthFirstDayStr() {
        return getLastMonthFirstDay().format(DEFAULT_FORMETTER);
    }


    /**
     * 拆分日期，最后的批次剩余一天时，会被合并到上一批次
     *
     * @param startDate
     * @param endDate
     * @param batchDays
     * @return
     */
    public static List<List<LocalDate>> splitDateBatches(LocalDate startDate, LocalDate endDate, int batchDays) {
        List<List<LocalDate>> result = new ArrayList<>();

        if (startDate.isAfter(endDate)) {
            return result;
        }

        List<LocalDate> allDates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            allDates.add(date);
        }

        int totalDays = allDates.size();
        int fullBatches = totalDays / batchDays;
        int remainder = totalDays % batchDays;

        int index = 0;
        for (int i = 0; i < fullBatches; i++) {
            int currentBatchSize = batchDays;

            // 如果最后一批只剩1天，就合并到当前批
            if (i == fullBatches - 1 && remainder == 1) {
                currentBatchSize += 1;
                remainder = 0; // 已合并
            }

            result.add(new ArrayList<>(allDates.subList(index, index + currentBatchSize)));
            index += currentBatchSize;
        }

        // 如果没有 fullBatches，也没有被合并，但有剩余的天数（比如仅一天）
        if (remainder > 0) {
            result.add(new ArrayList<>(allDates.subList(index, allDates.size())));
        }

        return result;
    }

    public static String getNextSunday(LocalDate now) {
        LocalDate nextSunday;
        // 判断当前日期是否为星期天
        if (now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            // 如果当前日期是星期天，直接获取下一个星期天
            nextSunday = now.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        } else {
            // 如果当前日期不是星期天，先将日期调整到本周的星期天，再获取下一个星期天
            nextSunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        }
        return nextSunday.format(TimeUtils.DEFAULT_FORMETTER);
    }

    //获取当前周的数据，返回结果 [0,1,0,1,1,0,0]，1表示日期在本周内
    public static List<Integer> dateToThisWeekList(String firstDate, Set<String> useDateList) {
        int[] weeks = new int[7];
        if (useDateList == null) {
            //数组转集合
            return Arrays.stream(weeks)
                    .boxed()
                    .collect(Collectors.toList());
        }
        // 本周第一天
        LocalDate startDate = LocalDate.parse(TimeUtils.getFirstDayWithWeek(LocalDate.parse(firstDate, TimeUtils.DEFAULT_FORMETTER)), TimeUtils.DEFAULT_FORMETTER);
        for (int i = 0; i < 7; i++) {
            if (useDateList.contains(startDate.format(TimeUtils.DEFAULT_FORMETTER))) {
                weeks[i] = 1;
            }
            startDate = startDate.plusDays(1);
        }

        return Arrays.stream(weeks)
                .boxed()
                .collect(Collectors.toList());
    }

    //获取月份的第一天
    public static LocalDate getFirstDayOfMonth(int month) {
        return LocalDate.now().withMonth(month).with(TemporalAdjusters.firstDayOfMonth());
    }

    //获取月份的最后一天
    public static LocalDate getLastDayOfMonth(int month) {
        return LocalDate.now().withMonth(month).with(TemporalAdjusters.lastDayOfMonth());
    }

    //获取月份的第一天
    public static LocalDate getFirstDayOfMonthForYear(int year, int month) {
        return LocalDate.now().withMonth(month).withYear(year).with(TemporalAdjusters.firstDayOfMonth());
    }

    //获取月份的最后一天
    public static LocalDate getLastDayOfMonthForYear(int year, int month) {
        return LocalDate.now().withMonth(month).withYear(year).with(TemporalAdjusters.lastDayOfMonth());
    }

    //获取两个日期中的较小者
    public static LocalDateTime min(LocalDateTime date1, LocalDateTime date2) {
        if (date1 == null) return date2;
        if (date2 == null) return date1;

        return date1.isBefore(date2) ? date1 : date2;
    }

    //获取两个日期中的较小者
    public static LocalDate min(LocalDate date1, LocalDate date2) {
        if (date1 == null) return date2;
        if (date2 == null) return date1;

        return date1.isBefore(date2) ? date1 : date2;
    }

    //获取两个日期中的较大者
    public static LocalDate max(LocalDate date1, LocalDate date2) {
        if (date1 == null) return date2;
        if (date2 == null) return date1;

        return date1.isAfter(date2) ? date1 : date2;
    }

    //获取今天到23:59:59的秒数
    public static long getToTodayEndSecond(LocalDateTime now) {
        // 获取当天的最后一秒，即当天的结束时间
        LocalDateTime endOfDay = now.toLocalDate().atStartOfDay().plusDays(1).minusSeconds(1);

        Duration duration = Duration.between(now, endOfDay);
        return duration.getSeconds();
    }

    /**
     * 判断指定日期是否在开始时间和结束时间范围内（包含边界）
     *
     * @param startDateStr  开始日期
     * @param endDateStr    结束日期
     * @param targetDateStr 目标日期
     * @return true: 目标日期在时间范围内, false: 目标日期不在时间范围内
     */
    public static boolean isDateInRange(String startDateStr, String endDateStr, String targetDateStr) {
        if (startDateStr == null || endDateStr == null || targetDateStr == null) {
            return false;
        }
        try {
            LocalDate startDate = LocalDate.parse(startDateStr, DEFAULT_FORMETTER);
            LocalDate endDate = LocalDate.parse(endDateStr, DEFAULT_FORMETTER);
            LocalDate targetDate = LocalDate.parse(targetDateStr, DEFAULT_FORMETTER);
            return (targetDate.isAfter(startDate) || targetDate.isEqual(startDate))
                    && (targetDate.isBefore(endDate) || targetDate.isEqual(endDate));
        } catch (Exception e) {
            return false;
        }
    }

    // 获取当天的最后一秒，即当天的结束时间
    public static LocalDateTime getEndOfDay(LocalDateTime now) {
        return now.toLocalDate().atStartOfDay().plusDays(1).minusSeconds(1);
    }

    //获取指定日期所在月的第一个周一
    public static LocalDate getFirstMondayWithMonth(YearMonth yearMonth) {
        return yearMonth.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
    }

    //获取指定日期所在月的最后一个周一
    public static LocalDate getLastMondayWithMonth(YearMonth yearMonth) {
        return yearMonth.atEndOfMonth().with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
    }

    /**
     * 使用Stream获取两个日期字符串之间的LocalDate集合
     *
     * @param beginStr 开始日期字符串 (格式: yyyy-MM-dd)
     * @param endStr   结束日期字符串 (格式: yyyy-MM-dd)
     * @return LocalDate集合
     */
    public static List<LocalDate> getDateListWithStream(String beginStr, String endStr) {
        try {
            LocalDate startDate = LocalDate.parse(beginStr, TimeUtils.DEFAULT_FORMETTER);
            LocalDate endDate = LocalDate.parse(endStr, TimeUtils.DEFAULT_FORMETTER);

            if (startDate.isAfter(endDate)) {
                return Collections.emptyList();
            }

            long days = startDate.until(endDate, ChronoUnit.DAYS);
            return Stream.iterate(startDate, date -> date.plusDays(1))
                    .limit(days + 1)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    //获取指定月份每周的周一与第几周的map
    public static Map<String, Integer> getFirstDayOfWeekAndWeekNum(YearMonth ym) {
        LocalDate startDate = TimeUtils.getFirstMondayWithMonth(ym);
        LocalDate endDate = LocalDate.parse(TimeUtils.getLastWeekDayWithMonth(ym.atEndOfMonth()), TimeUtils.DEFAULT_FORMETTER);
        int week = 1;
        Map<String, Integer> result = new HashMap<>();
        while (startDate.isBefore(endDate)) {
            result.put(startDate.format(TimeUtils.DEFAULT_FORMETTER), week++);
            startDate = startDate.plusWeeks(1);
        }
        return result;
    }

    /**
     * 获取指定日期所在周的周一的周数
     *
     * @param date 指定日期
     * @return 周数
     */
    public static int getWeekOfMonthByMonday(LocalDate date) {
        // 找到当前日期所在周的周一
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // 使用周一所在的月份来计算周数
        LocalDate firstDayOfMonth = monday.withDayOfMonth(1);

        // 找到该月的第一个周一
        LocalDate firstMonday = firstDayOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        // 如果第一个周一不在月份的第一天，则需要调整
        if (firstMonday.getDayOfMonth() > 7) {
            firstMonday = firstMonday.minusWeeks(1);
        }

        // 计算周数
        long weeksBetween = ChronoUnit.WEEKS.between(firstMonday, monday);
        int weekNumber = (int) weeksBetween + 1;

        return Math.max(1, Math.min(6, weekNumber));
    }

    /**
     * 获取两个日期之间的所有月份
     *
     * @param start
     * @param end
     * @return
     */
    public static List<YearMonth> getMonthsBetween(LocalDate start, LocalDate end) {
        List<YearMonth> months = new ArrayList<>();

        YearMonth startMonth = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(end);

        // 遍历所有月份
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            months.add(month);
        }

        return months;
    }

    /**
     * 获取两个日期之间的周一
     *
     * @param start
     * @param end
     * @return
     */
    public static Set<LocalDate> getMondaysBetween(LocalDate start, LocalDate end) {
        Set<LocalDate> dates = new HashSet<>();
        long untils = start.until(end, ChronoUnit.DAYS);
        for (long l = 0; l <= untils; l++) {
            LocalDate curDate = start.plusDays(l);
            // 所在周周一
            LocalDate weekBeginDate = curDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (dates.contains(weekBeginDate)) {
                continue;
            }
            dates.add(weekBeginDate);
        }
        return dates;
    }

    /**
     * 获取两个日期之间的周一
     * 并且周一在指定月份内
     *
     * @param start
     * @param end
     * @return
     */
    public static List<LocalDate> getMondaysBetweenMonths(LocalDate start, LocalDate end) {
        List<YearMonth> monthsBetween = TimeUtils.getMonthsBetween(start, end);
        List<LocalDate> dates = new ArrayList<>();
        long untils = start.until(end, ChronoUnit.DAYS);
        for (long l = 0; l <= untils; l++) {
            LocalDate curDate = start.plusDays(l);
            // 所在周周一
            LocalDate weekBeginDate = curDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (dates.contains(weekBeginDate)) {
                continue;
            }
            // 并且周一在指定月份内
            if (monthsBetween.contains(YearMonth.from(weekBeginDate))) {
                dates.add(weekBeginDate);
            }
        }
        return dates;
    }

    /**
     * 获取指定日期所在季度的第一天
     */
    public static String getFirstDayWithQuarter(LocalDate nowDate) {
        int monthValue = nowDate.getMonthValue();
        if (monthValue <= 3) {
            return TimeUtils.getFirstDayWithMonth(nowDate.withMonth(1));
        } else if (monthValue <= 6) {
            return TimeUtils.getFirstDayWithMonth(nowDate.withMonth(4));
        } else if (monthValue <= 9) {
            return TimeUtils.getFirstDayWithMonth(nowDate.withMonth(7));
        } else {
            return TimeUtils.getFirstDayWithMonth(nowDate.withMonth(10));
        }
    }

    /**
     * 获取指定日期所在季度的最后天
     */
    public static String getLastDayWithQuarter(LocalDate nowDate) {
        int monthValue = nowDate.getMonthValue();
        if (monthValue <= 3) {
            return TimeUtils.getLastDayWithMonth(nowDate.withMonth(3));
        } else if (monthValue <= 6) {
            return TimeUtils.getLastDayWithMonth(nowDate.withMonth(6));
        } else if (monthValue <= 9) {
            return TimeUtils.getLastDayWithMonth(nowDate.withMonth(9));
        } else {
            return TimeUtils.getLastDayWithMonth(nowDate.withMonth(12));
        }
    }

}