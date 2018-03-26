// A Matched-filter based Demodulator
// Jonathan Wang
package cdr

import chisel3._
import chisel3.util._

class MF(adc_width: Int = 5, template_length: Int = 40, t1I_Seq: Seq[SInt], t1Q_Seq: Seq[SInt], t2I_Seq: Seq[SInt], t2Q_Seq: Seq[SInt]) extends Module{
    val io = IO(new Bundle{
        val isig = Input(SInt(adc_width.W))
        // val qsig = Input(SInt(adc_width.W))
        val data_noclk = Output(UInt(1.W))
    })

    // Matched Filter f1
    val template1I = Module(new FIRfilter(length = template_length, inputwidth = adc_width, filterwidth = adc_width, import_coeffs = t1I_Seq))
    val template1Q = Module(new FIRfilter(length = template_length, inputwidth = adc_width, filterwidth = adc_width, import_coeffs = t1Q_Seq))

    val I1 = Wire(SInt((adc_width*3).W))
    val Q1 = Wire(SInt((adc_width*3).W))
    val mf1 = Wire(SInt((adc_width*6).W))

    template1I.io.in := io.isig
    template1Q.io.in := io.isig
    I1 := template1I.io.out
    Q1 := template1Q.io.out
    mf1 := I1*I1 + Q1*Q1


    // Matched Filter f2
    val template2I = Module(new FIRfilter(length = template_length, inputwidth = adc_width, filterwidth = adc_width, import_coeffs = t2I_Seq))
    val template2Q = Module(new FIRfilter(length = template_length, inputwidth = adc_width, filterwidth = adc_width, import_coeffs = t2Q_Seq))

    val I2 = Wire(SInt((adc_width*3).W))
    val Q2 = Wire(SInt((adc_width*3).W))
    val mf2 = Wire(SInt((adc_width*6).W))

    template2I.io.in := io.isig
    template2Q.io.in := io.isig
    I2 := template2I.io.out
    Q2 := template2Q.io.out
    mf2 := I2*I2 + Q2*Q2

    io.data_noclk := (mf1 < mf2).toBool
}

