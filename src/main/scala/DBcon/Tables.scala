package DBcon

import slick.jdbc.PostgresProfile.api._

object Tables {

  class t1(tag: Tag) extends Table[(String, String, String)](tag, Some("svr"),"t1") {
    def s_id = column[String]("s_id", O.PrimaryKey)
    def key = column[String]("key")
    def value = column[String]("value")
    def * = (s_id, key, value)
  }
  val t1 = TableQuery[t1]

  class t2(tag: Tag) extends Table[(String, String, String)](tag, "t2") {
    def s_id = column[String]("s_id")
    def key = column[String]("key")
    def value = column[String]("value")
    def * = (s_id, key, value)
  }

  class prova(tag: Tag) extends Table[(String, String, String)](tag, Some("svr"), "prova") {
    def s_id = column[String]("s_id")
    def key = column[String]("key")
    def value = column[String]("value")
    def * = (s_id, key, value)
 }

  class ApiResults(tag: Tag) extends Table[(String, String, String, String, String, String, String, String, String)](tag, Some("svr"), "apiresults"){
    def service = column[String]("service")
    def raw_value = column[String]("raw_value")
    def parsed_value = column[String]("parsed_value")
    def ontology = column[String]("ontology")
    def ontology_id = column[String]("ontology_id")
    def pref_label = column[String]("pref_label")
    def synonym = column[String]("synonym")
    def score = column[String]("score")
    def term_type = column[String]("term_type")
    def * = (service,raw_value,parsed_value,ontology,ontology_id,pref_label,synonym,score,term_type)
  }
}
