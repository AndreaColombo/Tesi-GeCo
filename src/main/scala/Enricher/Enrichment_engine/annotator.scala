package Enricher.Enrichment_engine

import java.net.{SocketTimeoutException, URLEncoder}
import java.sql.BatchUpdateException

import Enricher.DBCon.{default_values, db_handler, ontology_type, user_feedback_type}
import Recommender.Ontologies.Parsers.OlsParser.get_score
import Utilities.Preprocessing
import Utilities.score_calculator.get_match_score
import com.fasterxml.jackson.core.JsonParseException
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}
import scalaj.http._

import util.control.Breaks._
import Config.config.{get_anc_limit, get_desc_limit, get_ontologies_by_type}
import org.apache.log4j._


object annotator {
  val max_depth_anc: Int = get_anc_limit()
  val max_depth_desc: Int = get_desc_limit()

  val logger: Logger = Logger.getLogger(this.getClass)

  def get_info(source: String, iri: String): List[Map[String, String]] = {
    var result: List[Map[String, String]] = List()
    val tmp = ols_get_info(source,iri)
    if (tmp.nonEmpty) {
      val onto = tmp.head.head
      val parents = tmp.head(5)
      val children = tmp.head(6)

      val desc = get_desc(children, onto, 0)
      val anc = get_hyp(parents, onto, 0)

      if(!db_handler.cv_support_exists(onto,tmp.head(1)))
        result :+= Map("source" -> onto, "iri" -> tmp.head(1), "label" -> tmp.head(2), "xref" -> tmp.head(3), "syn" -> tmp.head(4), "parents" -> tmp.head(5), "part_of" -> tmp.head(7),"description"->tmp.head(8))

      //IN DESC CI SONO I DISCENDENTI DEL CURRENT TERM
      //IN ANC I SONO GLI ANCESTORS DEL CURRENT TERM

      for (tmp <- anc) {
        if(!db_handler.cv_support_exists(tmp._1,tmp._2))
        result :+= Map("source" -> tmp._1, "iri" -> tmp._2, "label" -> tmp._3, "xref" -> tmp._4, "syn" -> tmp._5, "parents" -> tmp._6, "part_of" -> tmp._8,"description"->tmp._9)
      }

      for (elem <- desc) {
        if (!db_handler.cv_support_exists(elem._1, elem._2))
        result :+= Map("source" -> elem._1, "iri" -> elem._2, "label" -> elem._3, "xref" -> elem._4, "syn" -> elem._5, "parents" -> elem._6, "part_of" -> elem._8,"description"->elem._9)
      }
    }
    result.distinct
  }

  def search_term(raw_value: String, term_type: String): (String, String) = {
    var res: List[(String, String, String, String, String, String, String, String)] = List()
    var result: (String, String) = ("","")
    val ontos = get_ontologies_by_type(term_type)
    var ok = false
    for (onto <- ontos if !ok){
      val tmp = ols_search_term(raw_value,onto)
      breakable {
        if (tmp._1 == "null")
        break()
        else {
          result = (tmp._1,tmp._2)
          ok = true
        }
      }
    }
    result
  }

  def get_user_feedback(value: String,table_name: String, term_type: String): Unit = {
    var user_feedback: List[user_feedback_type] = List()
    if ({user_feedback = ols_get_user_feedback(value, term_type, table_name); user_feedback.nonEmpty}) {
      try {
        db_handler.user_feedback_insert(user_feedback)
      }
      catch {
        case e: BatchUpdateException => logger.info("User feedback exception",e.getNextException)
      }
    }
    else {
      db_handler.user_feedback_insert(List(user_feedback_type(default_values.int,default_values.bool,table_name, term_type, null, value, null, null, null, null)))
    }
  }

  def get_desc(children: String, onto: String, depth: Int): List[(String, String, String, String, String, String, String, String,String)] = {
    var result: List[(String, String, String, String, String, String, String, String, String)] = List()
    for (code <- children.split(",")) {
      if (code != "null") {
        val res = ols_get_info(onto,code)
        if(res.nonEmpty) {
          result :+= (res.head.head, res.head(1), res.head(2), res.head(3), res.head(4), res.head(5), res.head(6), res.head(7), res.head(8))
          val n = depth + 1
          if (n != max_depth_desc)
            result ++= get_desc(res.head(6), res.head.head, n)
          else
            result
        }
      }
    }
    result
  }

  def get_hyp(parents: String, onto: String, depth: Int): List[(String, String, String, String, String, String, String, String,String)] = {
    var result: List[(String, String, String, String, String, String, String, String, String)] = List()
    for (code <- parents.split(",")) {
      if (code != "null") {
        val res = ols_get_info(onto,code)
        if (res.nonEmpty) {
          result :+= (res.head.head, res.head(1), res.head(2), res.head(3), res.head(4), res.head(5), res.head(6), res.head(7), res.head(8))
          val n = depth + 1
          if (n != max_depth_anc)
            result ++= get_hyp(res.head(5), res.head.head, n)
          else
            result
        }
      }
    }
    result
  }

  def ols_get_info(source: String, code: String): List[List[String]] = {
    var rows: Seq[List[String]] = List()
    var iri_tmp = ""
    try {
      iri_tmp = (Json.parse(Http(s"https://www.ebi.ac.uk/ols/api/ontologies/$source/terms").option(HttpOptions.readTimeout(50000)).asString.body) \\ "iri").head.validate[String].get
    }
    catch {
      case e: SocketTimeoutException => logger.info("Read timeout",e.getCause)
    }
    val iri = iri_tmp.substring(0,iri_tmp.lastIndexOf("/")+1)+code
    val url = s"https://www.ebi.ac.uk/ols/api/ontologies/$source/terms/"+URLEncoder.encode(URLEncoder.encode(iri, "UTF-8"), "UTF-8")

    if(ols_exist(source, iri)) {
      val response = Http(url).option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString
      val j = Json.parse(response.body)
      val prefLabel = (j \ "label").validate[String].get
      val ontology = source
      val ontology_id = code
      val description = (j \ "description").validate[List[String]].getOrElse(List("null")).head
      val synonym_l = (j \ "synonym").validate[List[String]].getOrElse(List("null"))
      val synonym = synonym_l.mkString(",")
      val xref = (j \ "annotation" \ "database_cross_reference").validate[List[String]].getOrElse(List("null"))

      var parents: List[String] = List()
      var part_of: List[String] = List()
      var children: List[String] = List()

      val children_url = url + "/hierarchicalChildren"
      val parents_url = url + "/parents"
      val part_url = url + "/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FBFO_0000050"

      val part_exist = (j \\ "part_of").nonEmpty

      val p_status = Http(parents_url).option(HttpOptions.readTimeout(50000)).asString.header("Status").get
      if (!(j \ "is_root").validate[Boolean].get && p_status.contains("200"))
        ((Json.parse(Http(parents_url).option(HttpOptions.readTimeout(50000)).asString.body) \ "_embedded").get("terms") \\ "short_form").foreach(a => parents :+= a.validate[String].getOrElse("null"))
      else parents = List("null")

      val pp_status = Http(part_url).option(HttpOptions.readTimeout(50000)).asString.header("Status").get
      if (part_exist && pp_status.contains("200"))
        ((Json.parse(Http(part_url).option(HttpOptions.readTimeout(50000)).asString.body) \ "_embedded").get("terms") \\ "short_form").foreach(a => part_of :+= a.validate[String].getOrElse("null"))
      else part_of = List("null")

      val c_status = Http(children_url).option(HttpOptions.readTimeout(50000)).asString.header("Status").get
      if ((j \ "has_children").validate[Boolean].get && c_status.contains("200"))
        ((Json.parse(Http(children_url).option(HttpOptions.readTimeout(50000)).asString.body) \ "_embedded").get("terms") \\ "short_form").foreach(a => children :+= a.validate[String].getOrElse("null"))
      else children = List("null")

      rows :+= List(ontology, ontology_id, prefLabel, xref.mkString(","), synonym, parents.mkString(","), children.mkString(","), part_of.mkString(","),description)
    }
    else {
      logger.info(s"OLS resource not available for $source, $code")
    }
    rows.toList.distinct
  }

  def ols_search_term(term: String, onto: String): (String, String) = {
    val url = "https://www.ebi.ac.uk/ols/api/search"
    val response = Http(url).param("q", term).param("fieldList", "label,short_form,synonym,ontology_name,iri").param("ontology", onto).param("rows", "5").option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.body
    var max_score = 0
    var result: (String, String)  = ("null", "null")
    var j: JsValue = null

    val logger = LoggerFactory.getLogger(this.getClass)
    try {
      j = (Json.parse(response) \ "response").get("docs")
    }
    catch {
      case e: JsonParseException => logger.info("json parse error",e)
    }

    val service = "Ols"
    val range = j \\ "label"
    for (i <- range.indices) {
      val j2 = j(i)
      val prefLabel = (j2 \ "label").validate[String].get
      val ontology = (j2 \ "ontology_name").validate[String].get
      val ontology_id = (j2 \ "short_form").validate[String].get
      val iri = (j2 \ "iri").validate[String].get
      val score_num = get_match_score(get_score(term, prefLabel), service)

      if (score_num > 6 && score_num > max_score) {
        if(ols_exist(ontology,iri)) {
          max_score = score_num
          result = (ontology, ontology_id)
        }
      }
    }
    result
  }

  def ols_exist(source: String, iri: String): Boolean = Http(s"https://www.ebi.ac.uk/ols/api/ontologies/$source/terms/"+URLEncoder.encode(URLEncoder.encode(iri, "UTF-8"), "UTF-8")).option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.header("status").get.contains("200")

  def ols_get_user_feedback(raw_value: String, term_type: String, table_name: String): List[user_feedback_type] = {
    var rows: List[user_feedback_type] = List()
    val parsed = Preprocessing.parse(List(raw_value)).split(",")
    for (value <- parsed) {
      val ontologies = get_ontologies_by_type(term_type)
      val url = "https://www.ebi.ac.uk/ols/api/search"
      val response = Http(url).param("q", value).param("fieldList", "label,short_form,ontology_name").param("ontology", ontologies.mkString(",")).param("rows", "5").option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.body
      val json = (Json.parse(response) \ "response").get("docs")
      for (k <- (json \\ "label").indices) {
        val jj = json(k)
        val label = (jj \ "label").validate[String].get
        val id = (jj \ "short_form").validate[String].get
        val onto = (jj \ "ontology_name").validate[String].get
        if (!rows.exists(_.code.get==id) && !db_handler.user_fb_exist(raw_value,onto,id))
          rows :+= user_feedback_type(default_values.int, default_values.bool, table_name, term_type, null, raw_value, Some(value), Some(label), Some(onto), Some(id))
      }
    }
    rows.distinct
  }

  def ols_get_onto_info(onto: String): ontology_type = {
    var result = ontology_type()
    val url = "https://www.ebi.ac.uk/ols/api/ontologies/"+onto
    val response = Http(url).option(HttpOptions.readTimeout(50000)).asString
    if(response.header("status").get.contains("200")) {
      val json = Json.parse(response.body)
      val source = onto
      val title = (json \ "config").get("title").validate[String].getOrElse(null)
      val description = (json \ "config").get("description").validate[String].getOrElse(null)
      result = ontology_type(source,Some(title),Some(description),Some(url))
    }
    else {
      result = ontology_type("other_link",null,null,null)
    }
    result
  }
}