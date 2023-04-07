package ammonite.spark.fromammonite

import ammonite.compiler.CodeClassWrapper
import ammonite.compiler.iface.CodeWrapper
import ammonite.interp.Interpreter
import ammonite.main.Defaults
import ammonite.repl._
import ammonite.repl.api.{FrontEnd, History, ReplLoad}
import ammonite.runtime.{Frame, ImportHook, Storage}
import ammonite.util.Util.normalizeNewlines
import ammonite.util._
import utest._

import scala.collection.mutable

/**
 * A test REPL which does not read from stdin or stdout files, but instead lets
 * you feed in lines or sessions programmatically and have it execute them.
 */
class TestRepl {
  var allOutput = ""
  def predef: (String, Option[os.Path]) = ("", None)
  def codeWrapper: CodeWrapper = CodeClassWrapper

  val tempDir = os.Path(
    java.nio.file.Files.createTempDirectory("ammonite-tester")
  )


  import java.io.ByteArrayOutputStream
  import java.io.PrintStream

  val outBytes = new ByteArrayOutputStream
  val errBytes = new ByteArrayOutputStream
  val resBytes = new ByteArrayOutputStream
  def outString = new String(outBytes.toByteArray)
  def resString = new String(resBytes.toByteArray)

  val warningBuffer = mutable.Buffer.empty[String]
  val errorBuffer = mutable.Buffer.empty[String]
  val infoBuffer = mutable.Buffer.empty[String]
  val printer0 = Printer(
    new PrintStream(outBytes),
    new PrintStream(errBytes),
    new PrintStream(resBytes),
    x => warningBuffer.append(x + Util.newLine),
    x => errorBuffer.append(x + Util.newLine),
    x => infoBuffer.append(x + Util.newLine)
  )
  val storage = new Storage.Folder(tempDir)

  private val initialLoader = Thread.currentThread().getContextClassLoader
  val frames: Ref[List[Frame]] = Ref(List(Frame.createInitial(initialLoader)))
  val sess0 = new SessionApiImpl(frames)

  var currentLine = 0
  val interpParams = Interpreter.Parameters(
    printer = printer0,
    storage = storage,
    wd = os.pwd,
    colors = Ref(Colors.BlackWhite),
    verboseOutput = true,
    alreadyLoadedDependencies = Defaults.alreadyLoadedDependencies("ammonite/spark/amm-test-dependencies.txt")
  )
  val interp = try {
    new Interpreter(
      compilerBuilder = ammonite.compiler.CompilerBuilder(
        outputDir = Some(os.temp.dir(prefix = "amm-spark-tests").toNIO)
      ),
      parser = () => ammonite.compiler.Parsers,
      getFrame = () => frames().head,
      createFrame = () => { val f = sess0.childFrame(frames().head); frames() = f :: frames(); f },
      replCodeWrapper = codeWrapper,
      scriptCodeWrapper = codeWrapper,
      parameters = interpParams
    )

  }catch{ case e: Throwable =>
    println(infoBuffer.mkString)
    println(outString)
    println(resString)
    println(warningBuffer.mkString)
    println(errorBuffer.mkString)
    throw e
  }

  val extraBridges = Seq((
    "ammonite.repl.ReplBridge",
    "repl",
    new ReplApiImpl {
      def replArgs0 = Vector.empty[Bind[_]]
      def printer = printer0

      def sess = sess0
      val prompt = Ref("@")
      val frontEnd = Ref[FrontEnd](null)
      def lastException: Throwable = null
      def fullHistory = storage.fullHistory()
      def history = new History(Vector())
      val colors = Ref(Colors.BlackWhite)
      def newCompiler() = interp.compilerManager.init(force = true)
      def _compilerManager = interp.compilerManager
      def fullImports = interp.predefImports ++ imports
      def imports = interp.frameImports
      def usedEarlierDefinitions = interp.frameUsedEarlierDefinitions
      def width = 80
      def height = 24

      object load extends ReplLoad with (String => Unit){

        def apply(line: String) = {
          interp.processExec(line, currentLine, () => currentLine += 1) match{
            case Res.Failure(s) => throw new CompilationError(s)
            case Res.Exception(t, s) => throw t
            case _ =>
          }
        }

        def exec(file: os.Path): Unit = {
          interp.watch(file)
          apply(normalizeNewlines(os.read(file)))
        }
      }
    }
  ))

  val basePredefs =
    if (predef._1.isEmpty) Nil
    else Seq(PredefInfo(Name("testPredef"), predef._1, false, predef._2))

  for ((error, _) <- interp.initializePredef(basePredefs, Seq(), extraBridges)) {
    val (msgOpt, causeOpt) = error match {
      case r: Res.Exception => (Some(r.msg), Some(r.t))
      case r: Res.Failure => (Some(r.msg), None)
      case _ => (None, None)
    }

    println(infoBuffer.mkString)
    println(outString)
    println(resString)
    println(warningBuffer.mkString)
    println(errorBuffer.mkString)
    throw new Exception(
      s"Error during predef initialization${msgOpt.fold("")(": " + _)}",
      causeOpt.orNull
    )
  }



  def session(sess: String): Unit = {
    // Remove the margin from the block and break
    // it into blank-line-delimited steps
    val margin = sess.linesIterator.filter(_.trim != "").map(_.takeWhile(_ == ' ').length).min
    // Strip margin & whitespace

    val steps = sess.replace(
      Util.newLine + margin, Util.newLine
    ).replaceAll(" *\n", "\n").split("\n\n")

    for((step, index) <- steps.zipWithIndex){
      // Break the step into the command lines, starting with @,
      // and the result lines
      val (cmdLines, resultLines) =
        step.linesIterator.toArray.map(_.drop(margin)).partition(_.startsWith("@"))

      val commandText = cmdLines.map(_.stripPrefix("@ ")).toVector

      println(cmdLines.mkString(Util.newLine))
      // Make sure all non-empty, non-complete command-line-fragments
      // are considered incomplete during the parse
      //
      // ...except for the empty 0-line fragment, and the entire fragment,
      // both of which are complete.
      for (incomplete <- commandText.inits.toSeq.drop(1).dropRight(1)){
        assert(ammonite.compiler.Parsers.split(incomplete.mkString(Util.newLine)).isEmpty)
      }

      // Finally, actually run the complete command text through the
      // interpreter and make sure the output is what we expect
      val expected = resultLines.mkString(Util.newLine).trim
      allOutput += commandText.map(Util.newLine + "@ " + _).mkString(Util.newLine)

      val (processed, out, res, warning, error, info) =
        run(commandText.mkString(Util.newLine), currentLine)

      val allOut = out + res

      if (expected.startsWith("error: ")) {
        val strippedExpected = expected.stripPrefix("error: ")
        assert(error.contains(strippedExpected))

      }else if (expected.startsWith("warning: ")){
        val strippedExpected = expected.stripPrefix("warning: ")
        assert(warning.contains(strippedExpected))

      }else if (expected.startsWith("info: ")){
        val strippedExpected = expected.stripPrefix("info: ")
        assert(info.contains(strippedExpected))

      }else if (expected == "") {
        processed match{
          case Res.Success(_) => // do nothing
          case Res.Skip => // do nothing
          case _: Res.Failing =>
            assert{
              identity(error)
              identity(warning)
              identity(out)
              identity(res)
              identity(info)
              false
            }
        }

      }else {
        processed match {
          case Res.Success(str) =>
            // Strip trailing whitespace
            def normalize(s: String) =
              s.linesIterator
                .map(_.replaceAll(" *$", ""))
                .mkString(Util.newLine)
                .trim()
            failLoudly(
              assert{
                identity(error)
                identity(warning)
                identity(info)
                normalize(allOut) == normalize(expected)
              }
            )

          case Res.Failure(failureMsg) =>
            assert{
              identity(error)
              identity(warning)
              identity(out)
              identity(res)
              identity(info)
              identity(expected)
              false
            }
          case Res.Exception(ex, failureMsg) =>
            val trace = Repl.showException(
              ex, fansi.Attrs.Empty, fansi.Attrs.Empty, fansi.Attrs.Empty
            ) + Util.newLine +  failureMsg
            assert({identity(trace); identity(expected); false})
          case _ => throw new Exception(
            s"Printed $allOut does not match what was expected: $expected"
          )
        }
      }
    }
  }



  def run(input: String, index: Int) = {

    outBytes.reset()
    resBytes.reset()
    warningBuffer.clear()
    errorBuffer.clear()
    infoBuffer.clear()
    val splitted = ammonite.compiler.Parsers.split(input) match {
      case None => sys.error(s"No result when splitting input '$input'")
      case Some(Left(error)) => sys.error(s"Error when splitting input '$input': $error")
      case Some(Right(stmts)) => stmts
    }
    val processed = interp.processLine(
      input,
      splitted,
      index,
      false,
      () => currentLine += 1
    )
    processed match{
      case Res.Failure(s) => printer0.error(s)
      case Res.Exception(throwable, msg) =>
        printer0.error(
          Repl.showException(throwable, fansi.Attrs.Empty, fansi.Attrs.Empty, fansi.Attrs.Empty)
        )

      case _ =>
    }
    Repl.handleOutput(interp, processed)
    (
      processed,
      outString,
      resString,
      warningBuffer.mkString,
      errorBuffer.mkString,
      infoBuffer.mkString
    )
  }


  def fail(input: String,
           failureCheck: String => Boolean = _ => true) = {
    val (processed, out, _, warning, error, info) = run(input, 0)

    processed match{
      case Res.Success(v) => assert({identity(v); identity(allOutput); false})
      case Res.Failure(s) =>
        failLoudly(assert(failureCheck(s)))
      case Res.Exception(ex, s) =>
        val msg = Repl.showException(
          ex, fansi.Attrs.Empty, fansi.Attrs.Empty, fansi.Attrs.Empty
        ) + Util.newLine + s
        failLoudly(assert(failureCheck(msg)))
      case _ => ???
    }
  }


  def result(input: String, expected: Res[Evaluated]) = {
    val (processed, allOut, _, warning, error, info) = run(input, 0)
    assert(processed == expected)
  }
  def failLoudly[T](t: => T) =
    try t
    catch{ case e: utest.AssertionError =>
      println("FAILURE TRACE" + Util.newLine + allOutput)
      throw e
    }

}
