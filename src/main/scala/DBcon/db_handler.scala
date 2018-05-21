package DBcon

import scala.concurrent._
import slick.jdbc.PostgresProfile.api._
import java.io._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import com.typesafe.config.ConfigFactory
import Tables.{ApiResults, ApiResults2, OntologyScore, best_ontos}

object db_handler {

  private val parsedConfig = ConfigFactory.parseFile(new File("src/main/scala/DBcon/application.conf"))
  private val conf = ConfigFactory.load(parsedConfig)
  private val db = Database.forConfig("mydb", conf)
  private val ApiResults = TableQuery[ApiResults]
  private val OntologyScore = TableQuery[OntologyScore]
  private val BestOntos = TableQuery[best_ontos]
  private val ApiResults2 = TableQuery[ApiResults2]

  val setup = DBIO.seq(ApiResults.schema.create)
  val setup2 = OntologyScore.schema.create
  val setup3  = BestOntos.schema.create
  val setupapiresults2 = ApiResults2.schema.create

  private val setupfuture = db.run(setup)
  db.run(setup2)
  db.run(setup3)
  db.run(setupapiresults2)

  def insert(rows: List[List[String]]) = {
    var ok: Seq[(String, String, String, String, String, String, String, String, String)] = Seq()

    for (l <- rows) {
      ok :+= (l(0), l(1), l(2), l(3), l(4), l(5), l(6), l(7), l(8))
    }
    actual_insert(ok)
    println("ok insert")
  }

  private def actual_insert(rows: Iterable[(String, String, String, String, String, String, String, String, String)]) = {
    val db = Database.forConfig("mydb", conf)
    val insertAction = ApiResults2 ++= rows
    val insert = db.run(insertAction)
    Await.result(insert, Duration.Inf)
    db.close()
  }

  def update_raw_value(rawValue: String, parsed: String) = {
    val db = Database.forConfig("mydb", conf)
    val q =
      sqlu"""update svr.apiresults2
             set raw_value = $rawValue
             where parsed_value = $parsed
          """

    val result_future = db.run(q)
    Await.result(result_future, Duration.Inf)
    db.close()
  }

  def insert_best_ontos(rows: Iterable[(String, String, Double, Double, Double)]) = {
    val db = Database.forConfig("mydb", conf)
    val insertAction = BestOntos ++= rows
    val insert = db.run(insertAction)
    Await.result(insert, Duration.Inf)
    db.close()
  }

  def update_score(suitability: Double, id: Int) = {
    val db = Database.forConfig("mydb", conf)
    val q =
      sqlu"""update svr.apiresults2
             set suitability = $suitability,
             where id = $id
          """

    val result_future = db.run(q)
    Await.result(result_future, Duration.Inf)
    db.close()
  }

  def update_term_type(parsedValue: String, term_type: String): Unit = {
    val q =
      sqlu"""update svr.apiresults2
            set term_type = $term_type
         where parsed_value ilike $parsedValue
          """
    val db = Database.forConfig("mydb", conf)
    Await.result(db.run(q), Duration.Inf)
    db.close()
  }

  def get_match_type(id: Int, service: String): String = {
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select score
           from svr.apiresults2
           where id = $id
           and service ilike $service
         """.as[String]

    var match_type = ""
    val future_match = db.run(q).map(a => match_type = a.head)

    Await.result(future_match, Duration.Inf)
    db.close()
    match_type
  }

  def ontology_score_insert(rows: Seq[(String, Double)]) = {
    val db = Database.forConfig("mydb", conf)
    val insertaction = OntologyScore ++= rows
    val result_future = db.run(insertaction)
    Await.result(result_future, Duration.Inf)
    db.close()
  }

  def get_ontology_by_type(term_type: String): List[String] = {
    var result: Seq[String] = List()
    val db = Database.forConfig("mydb", conf)

    val q =
      sql"""
           select distinct ontology
           from svr.apiresults2
           where term_type ilike $term_type
         """.as[String]
    val result_future = db.run(q).map(_.foreach(
      a => result :+= a
    ))
    Await.result(result_future, Duration.Inf)
    db.close()
    result.toList
  }

  def get_ontologies(): List[String] = {
    var result: Seq[String] = List()
    val db = Database.forConfig("mydb", conf)

    val q =
      sql"""
           select distinct ontology
           from svr.apiresults2
         """.as[String]
    val result_future = db.run(q).map(_.foreach(
      a => result :+= a
    ))
    Await.result(result_future, Duration.Inf)
    db.close()
    result.toList
  }

  def get_term_by_ontology(ontology: String, term_type: String): List[String] = {
    var result: Seq[String] = List()
    val db = Database.forConfig("mydb", conf)

    val q =
      sql"""
           select distinct raw_value
           from svr.apiresults2
           where term_type ilike $term_type and ontology ilike $ontology
         """.as[String]

    val result_future = db.run(q).map(_.foreach(
      a => result :+= a
    ))

    Await.result(result_future, Duration.Inf)
    db.close()
    result.toList
  }

  def get_parsed_by_ontology(ontology: String): List[String] = {
    var result: Seq[String] = List()
    val db = Database.forConfig("mydb", conf)

    val q =
      sql"""
           select distinct parsed_value
           from svr.apiresults2
           where ontology ilike $ontology
         """.as[String]

    val result_future = db.run(q).map(_.foreach(
      a => result :+= a
    ))

    Await.result(result_future, Duration.Inf)
    db.close()
    result.toList
  }

  def get_onto_score(onto: String, term_type: String): String = {
    val db = Database.forConfig("mydb", conf)
    var score = ""
    val q =
      sql"""
           select score
           from svr.ontologyscore
           where ontology ilike $onto
         """.as[String]
    val result_future = db.run(q).map(a => score = a.head)
    Await.result(result_future, Duration.Inf)
    db.close()
    score
  }

  def get_db_lenght(): Int = {
    val db = Database.forConfig("mydb", conf)
    var lenght = 0
    val q =
      sql"""
           select count(id)
           from svr.apiresults2
         """.as[Int]

    val result_future = db.run(q).map(a => lenght = a.head)
    Await.result(result_future, Duration.Inf)
    db.close()
    lenght
  }

  def get_onto_service_termtype(id: Int): (String, String, String) = {
    var result = ("", "", "")
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select ontology, service, term_type
           from svr.apiresults2
           where id = $id
         """.as[(String, String, String)]

    val result_future = db.run(q).map(a =>
      result = a.head
    )
    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }

  def get_onto_ontoid(id: Int): (String, String) = {
    var result = ("", "")
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select ontology, ontology_id
           from svr.apiresults2
           where id = $id
         """.as[(String, String)]

    val result_future = db.run(q).map(a =>
      result = a.head
    )
    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }

  def delete_row(id: Int): Unit = {
    val db = Database.forConfig("mydb", conf)
    val q =
      sqlu"""
            update svr.apiresults2
            set deleted = "true"
            where id = $id
          """
    val resultFuture = db.run(q)
    Await.result(resultFuture, Duration.Inf)
    db.close()
  }

  def get_onto_coverage(onto: String, term_type: String): (String, String) = {
    var result = ("", "")
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select count(distinct(raw_value)) / (select count(distinct(raw_value)) from svr.apiresults2 where term_type ilike $term_type)::Float, count(distinct(raw_value))
           from svr.apiresults2
           where ontology ilike $onto and term_type ilike $term_type
         """.as[(String, String)]

    val result_future = db.run(q).map(a =>
      result = a.head
    )
    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }

  def get_onto_matchscore(onto: String, term_type: String): String = {
    var result = ""
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select sum(match_score)
           from svr.apiresults2
           where ontology ilike $onto and term_type ilike $term_type
         """.as[String]

    val result_future = db.run(q).map(a =>
      result = a.head
    )
    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }

  def get_best_onto_per_term(term_type: String): Seq[(String, String, String, String, String)] = {
    var result = Seq(("", "", "", "", ""))
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select *
           from svr.best_onto_per_term
           where term_type = $term_type
         """.as[(String, String, String, String, String)]

    val result_future = db.run(q).map(a=>
      result = a
    )

    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }

  def get_nrv(term_type: String): Int = {
    var result = 0
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select nrv_count
           from svr.num_raw_values
           where term_type = $term_type
         """.as[Int]

    val result_future = db.run(q).map(a=>
      result = a.head
    )

    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }

  def get_score_suitability(onto: String, term_type: String): (Double, Double) = {
    var result: (Double, Double) = (0.0,0.0)
    val db = Database.forConfig("mydb", conf)
    val q =
      sql"""
           select avg_score, suitability
           from svr.best_onto_per_term
           where term_type = $term_type and ontology = $onto
         """.as[(Double, Double)]

    val result_future = db.run(q).map(a=>
      result = a.head
    )

    Await.result(result_future, Duration.Inf)
    db.close()
    result
  }
}