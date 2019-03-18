package cdr

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import chisel3.iotesters.{ChiselFlatSpec, Exerciser, PeekPokeTester, SteppedHWIOTester}
import org.scalatest.{FreeSpec, Matchers}
import java.io.File
import java.io.PrintWriter
import scala.io.Source


class CDRTester(c: CDR) extends PeekPokeTester(c) {
	val I_out = "./src/test/scala/cdr/test/I_out_noImage.csv"
	//val cdr_result = "./src/test/scala/cdr/test/data_exp.csv"
	val IStr = Source.fromFile(I_out).getLines().next
	val parsedIStr = IStr.split(",").map(_.trim)
	val isig = parsedIStr.map(_.toInt).

	//If want to check the error rate in Rocket rather than Matlab:
	//var data_out_exp = Seq[Int]()
	//for (line <- Source.fromFile(cdr_result).getLines.map(_.toInt)) {
	//data_out_exp = data_out_exp :+ line
	//}

	var data_out_demod = Seq[Int]()

	reset(5)
	var result_idx = 0

	for (v <- isig) {
		poke(c.io.isig, v)
		poke(c.io.data_out.ready, true.B)
		if(peek(c.io.data_out.valid) == 1) {
			data_out_demod = data_out_demod :+ peek(c.io.data_out.bits).toInt
			println(peek(c.io.data_out.bits).toInt.toString)
			result_idx = result_idx + 1
		}
		step(1)
	}


	val writer = new PrintWriter(new File("./src/test/scala/cdr/test/data_out_demod_cdr.csv"))
	for(v <- data_out_demod) {
		writer.write(v.toString+"\n")
	}
	writer.close()
}

class CDRTesterSpec extends FreeSpec with Matchers {
	"tester should do something" in {
  	iotesters.Driver.execute(Array("--backend-name", "firrtl", "--target-dir", "test_run_dir", "--fint-write-vcd"), () => new CDR(adc_width = 5, space_counter_width = 5, shift_bits = 40, CR_adjust_res = 4)) { c =>
  		new CDRTester(c)
  	} should be (true)
  }
}
