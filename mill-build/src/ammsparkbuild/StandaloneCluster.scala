package ammsparkbuild

import mill.*

trait StandaloneCluster extends Module {

  def sparkHome: T[PathRef]

  // Host the standalone cluster binds to
  def standaloneClusterHost: String = "localhost"

  def extraEnv: T[Map[String, String]] = Task(Map.empty)

  // Start a Spark standalone master against the local distribution
  def sparkMaster = Task.Worker {
    val home      = sparkHome().path
    val masterUrl = s"spark://$standaloneClusterHost:7077"
    val handle    = new StandaloneCluster.SparkMasterHandle(home, masterUrl, extraEnv())
    handle.tryClose()
    os.proc(home / "sbin" / "start-master.sh", "--host", standaloneClusterHost)
      .call(cwd = home, env = extraEnv(), stdin = os.Inherit, stdout = os.Inherit)
    handle
  }

  // Start a Spark standalone worker (slave) attached to sparkMaster
  def sparkSlave = Task.Worker {
    val home   = sparkHome().path
    val master = sparkMaster().master
    val handle = new StandaloneCluster.SparkSlaveHandle(home, extraEnv())
    handle.tryClose()
    os.proc(
      home / "sbin" / "start-slave.sh",
      "--host",
      standaloneClusterHost,
      master,
      "-c",
      "4",
      "-m",
      "4g"
    )
      .call(cwd = home, env = extraEnv(), stdin = os.Inherit, stdout = os.Inherit)
    handle
  }
}

object StandaloneCluster {

  // Handle on a running Spark standalone master. `master` is the SPARK_MASTER URL;
  // close() stops the master.
  class SparkMasterHandle(
    home: os.Path,
    val master: String,
    extraEnv: Map[String, String]
  ) extends AutoCloseable {
    private def close0(check: Boolean = true): Unit =
      os.proc(home / "sbin" / "stop-master.sh")
        .call(cwd = home, env = extraEnv, check = check, stdin = os.Inherit, stdout = os.Inherit)
    def tryClose(): Unit =
      close0(check = false)
    def close(): Unit =
      close0()
  }

  // Handle on a running Spark standalone worker (slave); close() stops it.
  class SparkSlaveHandle(
    home: os.Path,
    extraEnv: Map[String, String]
  ) extends AutoCloseable {
    private def close0(check: Boolean = true): Unit =
      os.proc(home / "sbin" / "stop-slave.sh")
        .call(cwd = home, env = extraEnv, check = check, stdin = os.Inherit, stdout = os.Inherit)
    def tryClose(): Unit =
      close0(check = false)
    def close(): Unit =
      close0()
  }

}
