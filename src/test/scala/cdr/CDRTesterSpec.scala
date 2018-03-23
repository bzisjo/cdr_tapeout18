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
	val I_out = "./src/test/scala/cdr/I_out_q.csv"
	// val Q_out = "./src/test/scala/cdr/Q_out.csv"
	val cdr_result = "./src/test/scala/cdr/data_exp.csv"
	val IStr = Source.fromFile(I_out).getLines().next
	val parsedIStr = IStr.split(",").map(_.trim)
	val isig = parsedIStr.map(_.toInt).toSeq

	var data_out_exp = Seq[Int]()
	for (line <- Source.fromFile(cdr_result).getLines.map(_.toInt)) {
		data_out_exp = data_out_exp :+ line
	}

	var data_out_demod = Seq[Int]()

	reset(5)
	var result_idx = 0
	// for (v <- isig) {
	// 	poke(c.io.isig, v)
	// 	poke(c.io.data_out.ready, true.B)
	// 	if(peek(c.io.data_out.valid) == 1) {
	// 		data_out_demod = data_out_demod :+ peek(c.io.data_out.bits).toInt
	// 		println(s"got:${peek(c.io.data_out.bits)}, exp:${data_out_exp.lift(result_idx)}")
	// 		expect(c.io.data_out.bits, data_out_exp.lift(result_idx))
	// 		result_idx = result_idx + 1
	// 	}
	// 	step(1)
	// }
	// for(i <- 1 until 80) {
	// 	poke(c.io.isig, 0)
	// 	poke(c.io.data_out.ready, true.B)
	// 	if(peek(c.io.data_out.valid) == 1) {
	// 		data_out_demod = data_out_demod :+ peek(c.io.data_out.bits).toInt
	// 		println(s"got:${peek(c.io.data_out.bits)}, exp:${data_out_exp(result_idx)}")
	// 		expect(c.io.data_out.bits, data_out_exp(result_idx))
	// 		result_idx = result_idx + 1
	// 	}
	// 	step(1)
	// }

	for (v <- isig) {
		poke(c.io.isig, v)
		poke(c.io.data_out.ready, true.B)
		if(peek(c.io.data_out.valid) == 1) {
			// println(s"got:${peek(c.io.data_out.bits)}")
			data_out_demod = data_out_demod :+ peek(c.io.data_out.bits).toInt
			println(peek(c.io.data_out.bits).toInt.toString)
			result_idx = result_idx + 1
		}
		step(1)
	}


	val writer = new PrintWriter(new File("data_out_demod.csv"))
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

// class CDRTesterSpec extends FlatSpec with Matchers {

//   // If you don't want to use default tester options, you need to create your own DspTesterOptionsManager
//   val testOptions = new DspTesterOptionsManager {
//     // Customizing Dsp-specific tester features (unspecified options remain @ default values)
//     dspTesterOptions = DspTesterOptions(
//         // # of bits of error tolerance allowed by expect (for FixedPoint, UInt, SInt type classes)
//         fixTolLSBs = 1,
//         // Generate a Verilog testbench to mimic peek/poke testing
//         genVerilogTb = true,
//         // Show all tester interactions with the module (not just failed expects) on the console
//         isVerbose = true)
//     // Customizing Chisel tester features
//     testerOptions = TesterOptions(
//         // If set to true, prints out all nested peeks/pokes (i.e. for FixedPoint or DspReal, would
//         // print out BigInt or base n versions of peeks/pokes -- rather than the proper decimal representation)
//         isVerbose = false)
//         // Default backend uses FirrtlInterpreter. If you want to simulate with the generated Verilog,
//         // you need to switch the backend to Verilator. Note that tests currently need to be dumped in 
//         // different output directories with Verilator; otherwise you run into weird concurrency issues (bug!)...
//         //backendName = "verilator"
//     // Override default output directory while maintaining other default settings
//     commonOptions = commonOptions.copy(targetDirName = "test_run_dir/CDR_tests")
//   }

//   behavior of "CDR Module"
//   it should "properly recover data at correct timing intervals" in {
//   	dsptools.Driver.execute(() => new CDR(SInt(5.W), adc_width = 5, space_counter_width = 5, IF_value = 15, shift_bits = 40, CR_adjust_res = 4), testOptions) { c => 
//   		new CDRTester(c)
//   	} should be (true)
//   }
// }