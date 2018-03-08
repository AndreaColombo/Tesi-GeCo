package Utils

import java.io._

object Utils {

  def escape (str: String) : String = {
    return str.replace('/', '-').replace('\\','-').replace(':','-').replace(';','-').replace('*','-').replace('?','-').replace('"','-').replace("--", "-")
  }

 def write_to_file (str: String, path_raw: String, filename: String, append: Boolean = false) : Boolean = {
   var path = ""
   if(path_raw.last == '/'){
     path = path_raw+escape(filename)
   }
   else {
     path = path_raw + "/" + escape(filename)
   }
   var writer: FileWriter = null
   var pw: PrintWriter = null
   var bw: BufferedWriter = null
   try {
     writer = new FileWriter(path)
     pw = new PrintWriter(writer)
     bw = new BufferedWriter(writer)
    }
    catch {
      case e: FileNotFoundException => {
        e.printStackTrace()
        return false
      }
      case e1: IOException => {
        e1.printStackTrace()
        return false
      }
    }
    if (append){
      pw.println(str)
    }
    else {
      writer.write(str)
      writer.flush()
    }
    writer.close()
    return true
  }
}
