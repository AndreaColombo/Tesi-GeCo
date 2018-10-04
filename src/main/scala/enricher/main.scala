package enricher

import java.util.Calendar

import config_pkg.ApplicationConfig
import enricher.dbcon.DbHandler
import engine.{Ols_interface, Annotator, Engine}
import org.apache.log4j.{FileAppender, Level, Logger, PatternLayout}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.{LoggerFactory}
import user_interface.ExpertPreference


object main extends App {
  val path = "C:/Users/Andrea Colombo/IdeaProjects/Tesi/"

  var i = 0

  def setup_logger(): Unit = {
    val PATTERN = "%d [%p] - %l %m%n"
    val logName = "log/lkb_normalizer_"+DateTime.now.toString(DateTimeFormat.forPattern("yyyy_MM_dd_HH_mm_ss_SSS")) + ".log"
    val fa2: FileAppender = new FileAppender()
    fa2.setName("FileLogger")
    fa2.setFile(logName)
    fa2.setLayout(new PatternLayout(PATTERN))
    fa2.setThreshold(Level.DEBUG)
    fa2.setAppend(true)
    fa2.activateOptions()
    Logger.getRootLogger.addAppender(fa2)
  }

  override def main(args: Array[String]): Unit = {
    try {
      //setup logger
      setup_logger()
      ApplicationConfig.conf.getObject("db_config")
      DbHandler.init()
      if (args.nonEmpty) {
        if (args(0).equals("reset")) {
          DbHandler.null_gcm()
          DbHandler.reset_db()
          DbHandler.init()
        }
        else if (args.length == 1) {
          if (args.head == "all") {
            val table_l = ApplicationConfig.get_gcm_table_list()
            for (t <- table_l) {
              val column_l = ApplicationConfig.get_termtype_list(t)
              for (col <- column_l) {
                Engine.controller(col)
              }
            }
          }
          else {
            val t = args(0)
            val column_l = ApplicationConfig.get_termtype_list(t)
            for (col <- column_l) {
              println(col)
              Engine.controller(col)
            }
          }
        }
        else {
          val t = args(0)
          val col = args(1)
          Engine.controller(col)
        }
      }
    }catch{
      case e:Exception =>
        val logger = LoggerFactory.getLogger(this.getClass)
        e.printStackTrace()
        logger.error("Error", e)
    }

  }

  def get_elapsed_time(d1: Long, d2: Long): Unit = {
    val elapsed:Double = (d2-d1).toDouble / 1000
    val min: Double = (elapsed / 60).intValue()
    val sec: Double = (((elapsed / 60) - min) * 60).intValue
    val millis = ((((elapsed / 60) - min) * 60) - sec) * 1000
    println(min.toInt + ":" + sec.toInt + ":" + millis.toInt)
  }

  def get_timestamp(): Unit = {
    val now = Calendar.getInstance()
    println(now.get(Calendar.HOUR_OF_DAY)+":"+now.get(Calendar.MINUTE)+":"+now.get(Calendar.SECOND))
  }
}