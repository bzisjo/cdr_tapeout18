// See LICENSE for license details.

package cdr

import chisel3._

class CDR(adc_width: Int = 5, fifo_width: Int = 8) extends Module{
  val io = IO(new Bundle {
    val I_stream = Input(SInt(adc_width.W))
    val data_out = Output(UInt(fifo_width.W))
  })
}