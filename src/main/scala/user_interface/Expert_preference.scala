package user_interface

import Config.config.{get_gcm_table_list, get_termtype_list}
import Enricher.DBCon.{db_handler, default_values, expert_preference_type}
import Enricher.Enrichment_engine.Ols_interface
import scalaj.http.{Http, HttpOptions}
import scalax.cli.Table
import shapeless.Sized

import scala.io.StdIn

object Expert_preference {

  def get_user_feedback(): Unit = {
    for (table_name <- get_gcm_table_list()){
      for (column_name <- get_termtype_list(table_name)){
        val raw_values = db_handler.get_user_feedback_raw_values(table_name,column_name)
        for (rv <- raw_values){
          var i = 0
          val options = db_handler.get_user_feedback_infos(rv)
          val table = Table(Sized("id","table name", "column name", "raw value", "parsed value","label","source","iri"))
          for (o <- options){
            table.rows += Sized(i.toString,o.table,o.column,o.raw_value,o.parsed_value.get,o.label.get,o.source.get,o.code.get)
            i+=1
          }
          table.alignments
          //CASE RAW VALUE NOT FOUND IN OLS LOOKUP
          if(!options.head.code.isDefined){
            table.print()
            display_prompt(false)
            val user_choice = input_source_code()
            val source = user_choice._1
            val code = user_choice._2
            val prefLabel = Ols_interface.ols_get_info(code,source).head(2)
            //INSERT IN USER REQUESTED CHOICE
            db_handler.insert_user_changes(expert_preference_type(default_values.int,table_name, column_name, rv, source, code))
          }
          //CASE RAW VALUE FOUND BUT NOT BEST MATCH
          else {
            table.print()
            val user_choice = get_input(i)
            if(user_choice.equalsIgnoreCase("manual insert")){
              println("Please input manually source and iri")
              val user_choice = input_source_code()
              val source = user_choice._1
              val code = user_choice._2
              val prefLabel = Ols_interface.ols_get_info(source,code).head(2)
              //INSERT IN USER REQUESTED CHOICE
              db_handler.insert_user_changes(expert_preference_type(default_values.int,table_name, column_name, rv, source, code))
            }
            else {
              val a = options(user_choice.toInt)
              db_handler.insert_user_changes(expert_preference_type(default_values.int,a.table,a.column,a.raw_value,a.source.get,a.code.get))
            }
          }
          db_handler.set_resolved(rv)
        }
      }
    }
  }

  def get_choice(range: Int): String = {
    var choice = ""
    var user_choice = StdIn.readLine()
    var id_choice = -1
    try id_choice = Integer.parseInt(user_choice)
    catch {
      case e: NumberFormatException => e
    }

    choice
  }


  def get_input(range: Int): String = {
    var user_choice = StdIn.readLine()
    var id_choice = -1
    try id_choice = Integer.parseInt(user_choice)
    catch {
      case e: NumberFormatException => e
    }
    while (!user_choice.equalsIgnoreCase("manual apiresults_insert") && ((id_choice<0) || (id_choice>range))) {
      println("Unknown command")
      user_choice = StdIn.readLine()
      try id_choice = Integer.parseInt(user_choice)
      catch {
        case e: NumberFormatException => e
      }
    }
    user_choice
  }

  def input_source_code(): (String, String) = {

    println("Please input source")
    var input = StdIn.readLine()
    while(!validate_source(input)) {
      println("Error, source not valid")
      println("Please input a valid source")
      input = StdIn.readLine()
      println(!validate_source(input))
    }
    val source = input

    println("Please input code in the form ONTO_XXXXXXXX")
    input = StdIn.readLine()
    while(!validate_code(source, input.map(_.toUpper))) {
      println("Error, code not valid")
      println("Please input a valid code")
      input = StdIn.readLine()
    }
    val code = input.map(_.toUpper)

    println()
    (source,code)
  }

  def display_prompt(flag: Boolean): Unit = {
    println("1 - MANUAL INSERT")
    if(flag) {
      println("2 - SELECT ID")
      println("3 - SKIP")
    }
    else println("2 - SKIP")

    println()
  }

  //return true if source is valid
  def validate_source(source: String): Boolean = {
    Http("https://www.ebi.ac.uk/ols/api/ontologies/"+source).option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.header("status").get.contains("200")
  }

  //return true if iri is valid
  def validate_code(onto: String, code: String): Boolean = {
    val iri = Ols_interface.ols_get_iri(onto,code)
    Ols_interface.ols_get_status(onto,iri).contains("200")
  }
}