package Recommender

import Config.config
import Utilities.score_calculator.{calculate_suitability_score,calculate_score,calculate_ontology_score}
import Recommender.DBCon.db_handler

object main {

  def main(args: Array[String]): Unit = {
    if(args.head.equals("score")) {
 //     calculate_ontology_score()
 //     calculate_score()
    }
    else {
      val t = args(0)
      val column_l = config.get_termtype_list(t)
//      for (col <- column_l) {
//        println(col)
//        db_filler.fill_db(t, col)
//        db_filler.update_db(t, col)
//      }
//	db_handler.create_view()
      for (col <- column_l) {
        //calculate_suitability_score(col)
	ontologies_set_calculator.calculate_ontology_set(col)
      }
    }
  }
}
