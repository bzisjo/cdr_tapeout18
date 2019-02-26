// Clock and Data Recovery with Matched-Filter Demodulator and Shift-Register Clock Recovery
package cdr

import chisel3._
import chisel3.util._

class MFCDR(adc_width: Int = 5, template_length: Int = 40, t1I_Seq: Seq[SInt], t1Q_Seq: Seq[SInt], t2I_Seq: Seq[SInt], t2Q_Seq: Seq[SInt], shift_bits: Int = 40, CR_adjust_res: Int = 4) extends Module{
    val io = IO(new Bundle{
        val isig = Input(SInt(adc_width.W))
        // val qsig = Input(SInt(adc_width.W))
        // Let us bypass the MF output straight into the core, if CR has issues
        val cr_bypass = Output(UInt(1.W))
        val data_out = Decoupled(UInt(1.W))
    })

    val demodulator = Module(new MF(adc_width = adc_width, template_length = template_length, t1I_Seq = t1I_Seq, t1Q_Seq = t1Q_Seq, t2I_Seq = t2I_Seq, t2Q_Seq = t2Q_Seq))
    io.cr_bypass := demodulator.io.data_noclk
    val cr_module = Module(new CR(shift_bits = shift_bits, CR_adjust_res = CR_adjust_res))

    demodulator.io.isig := io.isig
    // demodulator.io.qsig := io.qsig

    cr_module.io.data_noclk := demodulator.io.data_noclk
    io.data_out <> cr_module.io.data_out

}
