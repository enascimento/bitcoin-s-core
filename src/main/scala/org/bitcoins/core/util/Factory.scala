package org.bitcoins.core.util

/**
 * Created by chris on 2/26/16.
 * Trait to implement ubiquitous factory functions across our codebase
 */
trait Factory[T] extends BitcoinSLogger {

  /**
   * Creates a T out of a hex string
   * @param hex
   * @return
   */
  def fromHex(hex : String) : T = fromBytes(BitcoinSUtil.decodeHex(hex))

  /**
   * Creates a T out of a sequence of bytes
   * @param bytes
   * @return
   */
  def fromBytes(bytes : Seq[Byte]) : T

  /**
    * Creates a T out of a sequence of bytes
    * @param bytes
    * @return
    */
  def apply(bytes : Seq[Byte]) : T = fromBytes(bytes)

  /**
    * Creates a T from a hex string
    * @param hex
    * @return
    */
  def apply(hex : String) : T = fromHex(hex)
}
