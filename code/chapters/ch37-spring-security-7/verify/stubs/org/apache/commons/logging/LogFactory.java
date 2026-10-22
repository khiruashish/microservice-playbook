package org.apache.commons.logging; public abstract class LogFactory { public static Log getLog(Class<?> c){ return new Log(){}; } public static Log getLog(String n){ return new Log(){}; } }
