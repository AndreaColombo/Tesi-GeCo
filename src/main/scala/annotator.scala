import java.sql.BatchUpdateException

import DBcon.gecotest_handler
import Ontologies.Util.OlsParser
import Utils.Preprocessing
import play.api.libs.json.Json
import scalaj.http._
import com.github.tototoshi.csv._

object annotator {
  val max_depth = 2
  val url = "https://www.ebi.ac.uk/ols/api/search"

  def get_annotation(value: String, type_table_name: String, term_type: String): List[Map[String,String]] = {
    var res: List[(String, String, String, String, String, String, String, String)] = List()
    var result: List[Map[String, String]] = List()
    val ontos = Utils.Utils.get_ontologies_by_type(term_type)
    val response = Http(url).param("q", value).param("fieldList", "label,short_form,synonym,ontology_name,iri").param("ontology", ontos).param("rows", "5").option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.body
    val tmp = OlsParser.annotate(response, value, type_table_name, term_type)

    if (tmp.nonEmpty) {
      if (tmp.head.last == "GOOD") {
        println("good")
        val onto = tmp.head(0)
        val parents = tmp.head(5)
        val children = tmp.head(6)
        res :+= (tmp.head.head, tmp.head(1), tmp.head(2), tmp.head(3), tmp.head(4), tmp.head(5), tmp.head(6), tmp.head(7))

        val desc = get_desc(children, onto, 0)
        val anc = get_hyp(parents, onto, 0)
        result :+= Map("source" -> onto, "code" -> tmp.head(1), "label" -> tmp.head(2), "xref" -> tmp.head(3), "syn" -> tmp.head(4), "parents" -> tmp.head(5), "part_of" -> tmp.head(7))

        //IN DESC CI SONO I DISCENDENTI DEL CURRENT TERM
        //IN ANC I SONO GLI ANCESTORS DEL CURRENT TERM

        for (tmp <- anc) {
          result :+= Map("source" -> tmp._1, "code" -> tmp._2, "label" -> tmp._3, "xref" -> tmp._4, "syn" -> tmp._5, "parents" -> tmp._6, "part_of" -> tmp._8)
        }

        for (elem <- desc)
          result :+= Map("source" -> elem._1, "code" -> elem._2, "label" -> elem._3, "xref" -> elem._4, "syn" -> elem._5, "parents" -> elem._6, "part_of" -> elem._8)
      }
      else {
        gecotest_handler.user_feedback(tmp)
        println("user feedback")
      }
    }
    else {
      println("non trovato")
      try gecotest_handler.user_feedback(List(List(type_table_name,term_type,value,null,null,null,null)))
      catch {
        case e: BatchUpdateException => e.getNextException.printStackTrace()
      }
    }
    result.distinct
  }


  def get_desc(children: String,onto: String, depth: Int): List[(String, String, String, String, String, String, String, String)] = {
    var result: List[(String, String, String, String, String, String, String, String)] = List()
    for(value <- children.split(",")) {
      if (value != "null") {
        val res = OlsParser.enrich(value,onto)
        result :+= (res.head.head, res.head(1), res.head(2), res.head(3), res.head(4), res.head(5),res.head(6), res.head(7))
        val n = depth + 1
        if (n != max_depth)
          result ++= get_desc(res.head(6), res.head(0), n)
        else
          result
      }
    }
    result
  }
  def get_hyp(parents: String,onto: String, depth: Int): List[(String, String, String, String, String, String, String, String)] = {
    var result: List[(String, String, String, String, String, String, String, String)] = List()
    for(value <- parents.split(",")) {
      if (value != "null") {
        val res = OlsParser.enrich(value,onto)
        result :+= (res.head.head, res.head(1), res.head(2), res.head(3), res.head(4), res.head(5),res.head(6), res.head(7))
        val n = depth + 1
        if (n != max_depth)
          result ++= get_hyp(res.head(5), res.head(0), n)
        else
          result
      }
    }
    result
  }
}