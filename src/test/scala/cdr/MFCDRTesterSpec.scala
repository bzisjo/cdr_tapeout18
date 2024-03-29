package cdr

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import chisel3.iotesters.{ChiselFlatSpec, Exerciser, PeekPokeTester, SteppedHWIOTester}
import org.scalatest.{FreeSpec, Matchers}
import java.io.File
import java.io.PrintWriter
import scala.io.Source

class MFCDRTester(c: MFCDR) extends PeekPokeTester(c) {
    // We need the quantized, filtered/image rejected I and Q signals
    val isig_file = "./src/test/scala/cdr/test/I_out_noImage.csv"
    val isig = Source.fromFile(isig_file).getLines().next.split(",").map(_.trim).map(_.toInt).toSeq


    var data_out_demod = Seq[Int]()


    reset(5)

    for (v <- 1 until isig.length) {
        poke(c.io.isig, isig(v))
        //poke(c.io.qsig, qsig(v))
        poke(c.io.data_out.ready, true.B)
        if(peek(c.io.data_out.valid) == 1) {
            data_out_demod = data_out_demod :+ peek(c.io.data_out.bits).toInt
        }
        step(1)
    }

    val writer = new PrintWriter(new File("./src/test/scala/cdr/test/data_out_demod_mfcdr.csv"))
    for(v <- data_out_demod) {
        writer.write(v.toString+"\n")
    }
    writer.close()
}


class MFCDRTesterSpec extends FreeSpec with Matchers {
    val t1I_file = "./src/test/scala/cdr/template1I.csv"
    val t1Q_file = "./src/test/scala/cdr/template1Q.csv"
    val t2I_file = "./src/test/scala/cdr/template2I.csv"
    val t2Q_file = "./src/test/scala/cdr/template2Q.csv"

    val t1I:Seq[SInt] = Source.fromFile(t1I_file).getLines().next.split(",").map(_.trim).map(_.toInt).map(_.asSInt).toSeq
    val t1Q:Seq[SInt] = Source.fromFile(t1Q_file).getLines().next.split(",").map(_.trim).map(_.toInt).map(_.asSInt).toSeq
    val t2I:Seq[SInt] = Source.fromFile(t2I_file).getLines().next.split(",").map(_.trim).map(_.toInt).map(_.asSInt).toSeq
    val t2Q:Seq[SInt] = Source.fromFile(t2Q_file).getLines().next.split(",").map(_.trim).map(_.toInt).map(_.asSInt).toSeq
    "tester should demodulate things at correct timing intervals" in {
        iotesters.Driver.execute(Array("--backend-name", "firrtl", "--target-dir", "test_run_dir", "--fint-write-vcd"), () => new MFCDR(adc_width = 5, template_length = 40, t1I_Seq = t1I, t1Q_Seq = t1Q, t2I_Seq = t2I, t2Q_Seq = t2Q, shift_bits = 40, CR_adjust_res = 2)) { c =>
            new MFCDRTester(c)
        } should be (true)
    }
}