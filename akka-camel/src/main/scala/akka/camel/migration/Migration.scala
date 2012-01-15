package akka.camel.migration


object Migration{

  trait Bootable {
    def onLoad = {}
    def onUnload = {}
  }

  val config = new {
    def isModuleEnabled(s:String):Boolean = {

//      akka.config.Config.config.getList("akka.enabled-modules").exists(_ == "camel")
      true
    }
  }
  def unsupported = throw new UnsupportedOperationException
}