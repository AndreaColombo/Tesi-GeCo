package Recommender

import java.util.Calendar

import Ontologies.Ontology
import Recommender.DBCon.db_handler
import Utilities.Preprocessing.parse

object db_filler {

  val bioportal = Ontology.apply("bioportal")
  val recommender = Ontology.apply("recommender")
  val zooma = Ontology.apply("zooma")
  val ols = Ontology.apply("ols")

  def update_db (table_name: String, column_name: String): Unit = {
    val b = parse(db_handler.get_raw_values(table_name,column_name)).split(",")
    for (parsed <- b){
      db_handler.update_term_type(parsed, column_name)
    }
  }

  def fill_db (table_name: String, column_name: String): Unit = {
    val s = parse(db_handler.get_raw_values(table_name,column_name)).split(",").filterNot(_.isEmpty).mkString(",")
//    val c = s.split(",").filterNot(sd => sd.equalsIgnoreCase("p12")).mkString(",")
//    val b = c.split(",").filterNot(sd => sd.equalsIgnoreCase("h54")).mkString(",")
    println(column_name)
    get_timestamp()
//    println(s)
    val tmp = s.split(",")
    val tmp1 = tmp.splitAt(tmp.length / 2)._1.toList
    val tmp2 = tmp.splitAt(tmp.length / 2)._2.toList
    val recsys1 = tmp1.splitAt(tmp1.length / 2)._1.mkString(",")
    val recsys2 = tmp1.splitAt(tmp1.length / 2)._2.mkString(",")
    val recsys3 = tmp2.splitAt(tmp2.length / 2)._1.mkString(",")
    val recsys4 = tmp2.splitAt(tmp2.length / 2)._2.mkString(",")
//    println(tmp1.length)
//    println(tmp2.length)
//    println(recsys1.split(",").length)
//    println(recsys2.split(",").length)
//    println(recsys3.split(",").length)
//    println(recsys4.split(",").length)

    println("bioportal inizio")
    println(s)
    db_handler.apiresults_insert(bioportal.input(s))
    println("bioportal fine")
    get_timestamp()


    println("recsys 1 inizio")
    db_handler.apiresults_insert(recommender.input(recsys1))
    println("recsys 1 fine")
    get_timestamp()

    println("recsys 2 inizio")
    db_handler.apiresults_insert(recommender.input(recsys2))
    println("recsys 2 fine")
    get_timestamp()

    println("recsys 3 inizio")
    db_handler.apiresults_insert(recommender.input(recsys3))
    println("recsys 3 fine")
    get_timestamp()

    println("recsys 4 inizio")
    db_handler.apiresults_insert(recommender.input(recsys4))
    println("recsys 4 fine")
    get_timestamp()

    println("zooma inizio")
    db_handler.apiresults_insert(zooma.input(s))
    println("zooma fine")
    get_timestamp()

    println("ols inizio")
    db_handler.apiresults_insert(ols.input(s))
    println("ols fine")
    get_timestamp()
  }

  def get_timestamp(): Unit = {
    val now = Calendar.getInstance()
    println(now.get(Calendar.HOUR_OF_DAY)+":"+now.get(Calendar.MINUTE)+":"+now.get(Calendar.SECOND))
  }
}