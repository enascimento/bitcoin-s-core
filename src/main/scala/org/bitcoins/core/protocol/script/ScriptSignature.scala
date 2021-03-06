package org.bitcoins.core.protocol.script

import org.bitcoins.core.crypto.{ECDigitalSignature, ECPublicKey}
import org.bitcoins.core.protocol.NetworkElement
import org.bitcoins.core.script.constant._
import org.bitcoins.core.serializers.script.{RawScriptSignatureParser, ScriptParser}
import org.bitcoins.core.util.{BitcoinSLogger, BitcoinSUtil, BitcoinScriptUtil, Factory}

import scala.util.{Failure, Success, Try}

/**
  * Created by chris on 12/26/15.
  *
  */
sealed trait ScriptSignature extends NetworkElement with BitcoinSLogger {
  /**
    * Representation of a scriptSignature in a parsed assembly format
    * this data structure can be run through the script interpreter to
    * see if a script evaluates to true
    *
    * @return
    */
  lazy val asm : Seq[ScriptToken] = ScriptParser.fromHex(hex)



  /**
    * The digital signatures contained inside of the script signature
    * p2pkh script signatures only have one sig
    * p2pk script signatures only have one sigs
    * p2sh script signatures can have m sigs
    * multisignature scripts can have m sigs
    *
    * @return
    */
  def signatures : Seq[ECDigitalSignature]

}

sealed trait NonStandardScriptSignature extends ScriptSignature {
  def signatures : Seq[ECDigitalSignature] = Seq()
}

object NonStandardScriptSignature extends Factory[NonStandardScriptSignature] {
  private case class NonStandardScriptSignatureImpl(hex : String) extends NonStandardScriptSignature

  override def fromBytes(bytes: Seq[Byte]): NonStandardScriptSignature = {
    //make sure we can parse the bytes
    val asm = ScriptParser.fromBytes(bytes)
    NonStandardScriptSignature.fromAsm(asm)
  }

  def fromAsm(asm : Seq[ScriptToken]): NonStandardScriptSignature = {
    val hex = asm.map(_.hex).mkString
    NonStandardScriptSignatureImpl(hex)
  }
}

/**
 * P2PKH script signatures have only one public key
 * https://bitcoin.org/en/developer-guide#pay-to-public-key-hash-p2pkh
 * P2PKH scriptSigs follow this format
 * <sig> <pubkey>
 */
sealed trait P2PKHScriptSignature extends ScriptSignature {

  /**
    * P2PKH scriptSigs only have one signature
    *
    * @return
    */
  def signature : ECDigitalSignature = signatures.head

  /**
    * Gives us the public key inside of a p2pkh script signature
    *
    * @return
    */
  def publicKey : ECPublicKey = ECPublicKey(asm.last.bytes)

  override def signatures : Seq[ECDigitalSignature] = {
    Seq(ECDigitalSignature(asm(1).hex))
  }

}

object P2PKHScriptSignature extends Factory[P2PKHScriptSignature] {
  private case class P2PKHScriptSignatureImpl(hex : String) extends P2PKHScriptSignature

  override def fromBytes(bytes : Seq[Byte]): P2PKHScriptSignature = {
    val asm = ScriptParser.fromBytes(bytes)
    P2PKHScriptSignature.fromAsm(asm)
  }

  def fromAsm(asm: Seq[ScriptToken]): P2PKHScriptSignature = {
    require(isP2PKHScriptSig(asm), "Given asm was not a P2PKHScriptSignature, got: " + asm)
    val hex = asm.map(_.hex).mkString
    P2PKHScriptSignatureImpl(hex)
  }

  /**
    * Builds a script signature from a digital signature and a public key
    * this is a pay to public key hash script sig
    *
    * @param signature
    * @param pubKey
    * @return
    */
  def apply(signature : ECDigitalSignature, pubKey : ECPublicKey) : P2PKHScriptSignature = {
    val signatureBytesToPushOntoStack = BitcoinScriptUtil.calculatePushOp(signature.bytes)
    val pubKeyBytesToPushOntoStack = BitcoinScriptUtil.calculatePushOp(pubKey.bytes)
    val asm : Seq[ScriptToken] = signatureBytesToPushOntoStack ++ Seq(ScriptConstant(signature.hex)) ++
      pubKeyBytesToPushOntoStack ++ Seq(ScriptConstant(pubKey.hex))
    fromAsm(asm)
  }

  /**
    * Determines if the given asm matches a [[P2PKHScriptSignature]]
    *
    * @param asm
    * @return
    */
  def isP2PKHScriptSig(asm: Seq[ScriptToken]): Boolean = asm match {
    case List(w : BytesToPushOntoStack, x : ScriptConstant, y : BytesToPushOntoStack,
      z : ScriptConstant) => true
    case _ => false
  }
}

/**
 * Represents a pay-to-script-hash script signature
 * https://bitcoin.org/en/developer-guide#pay-to-script-hash-p2sh
 * P2SH scriptSigs have the following format
 * <sig> [sig] [sig...] <redeemScript>
 */
sealed trait P2SHScriptSignature extends ScriptSignature {

  /**
    * The redeemScript represents the conditions that must be satisfied to spend the output
    *
    * @return
    */
  def redeemScript : ScriptPubKey = ScriptPubKey(asm.last.bytes)


  /**
    * Returns the script signature of this p2shScriptSig with no serialized redeemScript
    *
    * @return
    */
  def scriptSignatureNoRedeemScript = ScriptSignature.fromAsm(splitAtRedeemScript(asm)._1)


  /**
    * Returns the public keys for the p2sh scriptSignature
    *
    * @return
    */
  def publicKeys : Seq[ECPublicKey] = {
    val pubKeys : Seq[ScriptToken] = redeemScript.asm.filter(_.isInstanceOf[ScriptConstant])
      .filterNot(_.isInstanceOf[ScriptNumberOperation])
    pubKeys.map(k => ECPublicKey(k.hex))
  }


  /**
    * The digital signatures inside of the scriptSig
    *
    * @return
    */
  def signatures : Seq[ECDigitalSignature] = {
    val nonRedeemScript = splitAtRedeemScript(asm)._1
    val sigs = nonRedeemScript.filter(_.isInstanceOf[ScriptConstant]).filterNot(_.isInstanceOf[ScriptNumberOperation]).filterNot(_.hex.length < 100)
    sigs.map(s => ECDigitalSignature(s.hex))
  }


  /**
    * Splits the given asm into two parts
    * the first part is the digital signatures
    * the second part is the redeem script
    *
    * @param asm
    * @return
    */
  def splitAtRedeemScript(asm : Seq[ScriptToken]) : (Seq[ScriptToken],Seq[ScriptToken]) = {
    //call .tail twice to remove the serialized redeemScript & it's bytesToPushOntoStack constant
    (asm.reverse.tail.tail.reverse, Seq(asm.last))
  }
}

object P2SHScriptSignature extends Factory[P2SHScriptSignature] with BitcoinSLogger {
  private case class P2SHScriptSignatureImpl(hex : String) extends P2SHScriptSignature

  override def fromBytes(bytes : Seq[Byte]): P2SHScriptSignature = {
    val asm = ScriptParser.fromBytes(bytes)
    P2SHScriptSignature.fromAsm(asm)
  }

  def apply(scriptSig : ScriptSignature, redeemScript : ScriptPubKey): P2SHScriptSignature = {
    //we need to calculate the size of the redeemScript and add the corresponding push op
    val pushOps = BitcoinScriptUtil.calculatePushOp(ScriptConstant(redeemScript.bytes))
    val bytes = scriptSig.bytes ++ pushOps.flatMap(_.bytes) ++ redeemScript.bytes
    fromBytes(bytes)
  }

  def fromAsm(asm: Seq[ScriptToken]): P2SHScriptSignature = {
    require(isP2SHScriptSig(asm), "Given asm tokens are not a p2sh scriptSig, got: " + asm)
    val hex = asm.map(_.hex).mkString
    P2SHScriptSignatureImpl(hex)
  }

  /**
    * Tests if the given asm tokens are a [[P2SHScriptSignature]]
    *
    * @param asm
    * @return
    */
  def isP2SHScriptSig(asm: Seq[ScriptToken]): Boolean = asm match {
    case _ if asm.size > 1 && isRedeemScript(asm.last) => true
    case _ => false
  }

  /**
    * Detects if the given script token is a redeem script
    *
    * @param token
    * @return
    */
  def isRedeemScript(token : ScriptToken) : Boolean = {
    logger.debug("Checking if last token is redeem script")
    val redeemScriptTry : Try[ScriptPubKey] = parseRedeemScript(token)
    redeemScriptTry match {
      case Success(redeemScript) =>
        logger.debug("Possible redeemScript: " + redeemScript)
        redeemScript match {
          case x : P2PKHScriptPubKey => true
          case x : MultiSignatureScriptPubKey => true
          case x : P2SHScriptPubKey => true
          case x : P2PKScriptPubKey => true
          case x : CLTVScriptPubKey => true
          case x : CSVScriptPubKey => true
          case x : NonStandardScriptPubKey => false
          case EmptyScriptPubKey => false
        }
      case Failure(_) => false
    }
  }


  /**
    * Parses a redeem script from the given script token
    *
    * @param scriptToken
    * @return
    */
  def parseRedeemScript(scriptToken : ScriptToken) : Try[ScriptPubKey] = {
    val redeemScript : Try[ScriptPubKey] = Try(ScriptPubKey(scriptToken.bytes))
    redeemScript
  }
}

/**
 * Represents a multisignature script signature
 * https://bitcoin.org/en/developer-guide#multisig
 * Multisig script sigs have the following format
 * OP_0 <A sig> [B sig] [C sig...]
 */
sealed trait MultiSignatureScriptSignature extends ScriptSignature {

  /**
    * The digital signatures inside of the scriptSig
    *
    * @return
    */
  def signatures : Seq[ECDigitalSignature] = {
    asm.tail.filter(_.isInstanceOf[ScriptConstant])
      .map(sig => ECDigitalSignature(sig.hex))
  }
}

object MultiSignatureScriptSignature extends Factory[MultiSignatureScriptSignature] {

  private case class MultiSignatureScriptSignatureImpl(hex : String) extends MultiSignatureScriptSignature

  override def fromBytes(bytes : Seq[Byte]): MultiSignatureScriptSignature = {
    val asm = ScriptParser.fromBytes(bytes)
    MultiSignatureScriptSignature.fromAsm(asm)
  }

  def apply(signatures : Seq[ECDigitalSignature]): MultiSignatureScriptSignature = {
    val sigsPushOpsPairs : Seq[Seq[ScriptToken]] = for {
      sig <- signatures
      constant = ScriptConstant(sig.bytes)
      pushOps = BitcoinScriptUtil.calculatePushOp(sig.bytes)
    } yield pushOps ++ Seq(constant)
    val sigsWithPushOps = sigsPushOpsPairs.flatten
    //OP_0 is for the dummy input required by OP_CHECKMULTISIG
    val asm = OP_0 +: sigsWithPushOps
    MultiSignatureScriptSignature.fromAsm(asm)
  }

  def fromAsm(asm: Seq[ScriptToken]): MultiSignatureScriptSignature = {
    require(isMultiSignatureScriptSignature(asm), "The given asm tokens were not a multisignature script sig: " + asm)
    val hex = asm.map(_.hex).mkString
    MultiSignatureScriptSignatureImpl(hex)
  }

  /**
    * Checks if the given script tokens are a multisignature script sig
    * format: OP_0 <A sig> [B sig] [C sig...]
    *
    * @param asm the asm to check if it falls in the multisignature script sig format
    * @return boolean indicating if the scriptsignature is a multisignature script signature
    */
  def isMultiSignatureScriptSignature(asm : Seq[ScriptToken]) : Boolean = asm.isEmpty match {
    case true => false
    //case false if (asm.size == 1) => false
    case false =>
      val firstTokenIsScriptNumberOperation = asm.head.isInstanceOf[ScriptNumberOperation]
      val restOfScriptIsPushOpsOrScriptConstants = asm.tail.map(
        token => token.isInstanceOf[ScriptConstant] || StackPushOperationFactory.isPushOperation(token)
      ).exists(_ == false)
      logger.debug("First number is script op: " + firstTokenIsScriptNumberOperation)
      logger.debug("tail is true: " +restOfScriptIsPushOpsOrScriptConstants )
      firstTokenIsScriptNumberOperation && !restOfScriptIsPushOpsOrScriptConstants
  }
}

/**
 * Represents a pay to public key script signature
 * https://bitcoin.org/en/developer-guide#pubkey
 * Signature script: <sig>
 */
sealed trait P2PKScriptSignature extends ScriptSignature {

  /**
    * PubKey scriptSignatures only have one signature
    *
    * @return
    */
  def signature : ECDigitalSignature = signatures.head

  /**
    * The digital signatures inside of the scriptSig
    *
    * @return
    */
  def signatures : Seq[ECDigitalSignature] = {
    Seq(ECDigitalSignature(BitcoinScriptUtil.filterPushOps(asm).head.hex))
  }
}

object P2PKScriptSignature extends Factory[P2PKScriptSignature] {
  private case class P2PKScriptSignatureImpl(hex : String) extends P2PKScriptSignature

  def apply(signature: ECDigitalSignature): P2PKScriptSignature = {
    val pushOps = BitcoinScriptUtil.calculatePushOp(signature.bytes)
    val signatureConstant = ScriptConstant(signature.bytes)
    val asm = pushOps ++ Seq(signatureConstant)
    P2PKScriptSignature.fromAsm(asm)
  }

  def fromAsm(asm: Seq[ScriptToken]): P2PKScriptSignature = {
    require(isP2PKScriptSignature(asm), "The given asm tokens were not a p2pk scriptSig, got: " + asm)
    val hex = asm.map(_.hex).mkString
    P2PKScriptSignatureImpl(hex)
  }

  override def fromBytes(bytes: Seq[Byte]): P2PKScriptSignature = {
    val asm = ScriptParser.fromBytes(bytes)
    P2PKScriptSignature.fromAsm(asm)
  }

  /**
    * P2PK scriptSigs always have the pattern [pushop, digitalSignature]
    *
    * @param asm
    * @return
    */
  def isP2PKScriptSignature(asm: Seq[ScriptToken]): Boolean = asm match {
    case List(w : BytesToPushOntoStack, x : ScriptConstant) => true
    case _ => false
  }
}

sealed trait CLTVScriptSignature extends ScriptSignature {
  def scriptSig : ScriptSignature = ScriptSignature(hex)

  override def signatures : Seq[ECDigitalSignature] = scriptSig.signatures

  override def hex = scriptSig.hex
}

object CLTVScriptSignature extends Factory[CLTVScriptSignature] {
  private case class CLTVScriptSignatureImpl(override val hex : String) extends CLTVScriptSignature

  override def fromBytes(bytes : Seq[Byte]) : CLTVScriptSignature = {
    val hex = BitcoinSUtil.encodeHex(bytes)
    fromHex(hex)
  }

  override def fromHex(hex : String) : CLTVScriptSignature = {
    CLTVScriptSignatureImpl(hex)
  }

  def apply(scriptSig : ScriptSignature) : CLTVScriptSignature = {
    fromHex(scriptSig.hex)
  }

  /**
    * Creates a CLTVScriptSignature out the [[ScriptPubKey]] we are satisfying, a sequence of [[ECDigitalSignature]], and a sequence
    * of [[ECPublicKey]] needed to satisfy the scriptPubKey. If a [[P2SHScriptPubKey]] is provided, a redeemScript must also be provided.
    * @return
    */
  def apply(scriptPubKey: ScriptPubKey, sigs : Seq[ECDigitalSignature], pubKeys : Seq[ECPublicKey]) : CLTVScriptSignature = scriptPubKey match {
    case p2pkScriptPubKey : P2PKScriptPubKey => CLTVScriptSignature(P2PKScriptSignature(sigs.head))
    case p2pkhScriptPubKey : P2PKHScriptPubKey => CLTVScriptSignature(P2PKHScriptSignature(sigs.head, pubKeys.head))
    case multiSigScriptPubKey : MultiSignatureScriptPubKey => CLTVScriptSignature(MultiSignatureScriptSignature(sigs))
    case cltvScriptPubKey : CLTVScriptPubKey => apply(cltvScriptPubKey.scriptPubKeyAfterCLTV, sigs, pubKeys)
    case csvScriptPubKey : CSVScriptPubKey => apply(csvScriptPubKey.scriptPubKeyAfterCSV, sigs, pubKeys)
    case EmptyScriptPubKey => CLTVScriptSignature(EmptyScriptSignature)
    case x @ (_ : NonStandardScriptPubKey | _ : P2SHScriptPubKey) => throw new IllegalArgumentException("A NonStandardScriptSignature or P2SHScriptSignature cannot be" +
      "the underlying scriptSig in a CLTVScriptSignature. Got: " + x)
  }

}

sealed trait CSVScriptSignature extends ScriptSignature {
  def scriptSig : ScriptSignature = ScriptSignature(hex)

  override def signatures : Seq[ECDigitalSignature] = scriptSig.signatures

  override def hex = scriptSig.hex
}

object CSVScriptSignature extends Factory[CSVScriptSignature] {
  private case class CSVScriptSignatureImpl(override val hex : String) extends CSVScriptSignature

  override def fromBytes(bytes : Seq[Byte]) : CSVScriptSignature = {
    val hex = BitcoinSUtil.encodeHex(bytes)
    fromHex(hex)
  }

  override def fromHex(hex : String) : CSVScriptSignature = {
    CSVScriptSignatureImpl(hex)
  }

  def apply(scriptSig : ScriptSignature) : CSVScriptSignature = {
    fromHex(scriptSig.hex)
  }
  /**
    * Creates a CSVScriptSignature out the [[ScriptPubKey]] we are satisfying, a sequence of [[ECDigitalSignature]], and a sequence
    * of [[ECPublicKey]] needed to satisfy the scriptPubKey. If a [[P2SHScriptPubKey]] is provided, a redeemScript must also be provided.
    * @return
    */
  def apply(scriptPubKey: ScriptPubKey, sigs : Seq[ECDigitalSignature], pubKeys : Seq[ECPublicKey]) : CSVScriptSignature = scriptPubKey match {
    case p2pkScriptPubKey : P2PKScriptPubKey => CSVScriptSignature(P2PKScriptSignature(sigs.head))
    case p2pkhScriptPubKey : P2PKHScriptPubKey => CSVScriptSignature(P2PKHScriptSignature(sigs.head, pubKeys.head))
    case multiSigScriptPubKey : MultiSignatureScriptPubKey => CSVScriptSignature(MultiSignatureScriptSignature(sigs))
    case cltvScriptPubKey : CLTVScriptPubKey => apply(cltvScriptPubKey.scriptPubKeyAfterCLTV, sigs, pubKeys)
    case csvScriptPubKey : CSVScriptPubKey => apply(csvScriptPubKey.scriptPubKeyAfterCSV, sigs, pubKeys)
    case EmptyScriptPubKey => CSVScriptSignature(EmptyScriptSignature)
    case x @ (_ : NonStandardScriptPubKey | _ : P2SHScriptPubKey) => throw new IllegalArgumentException("A NonStandardScriptSignature or P2SHScriptSignature cannot be" +
      "the underlying scriptSig in a CSVScriptSignature. Got: " + x)
  }
}


/**
 * Represents the empty script signature
 */
case object EmptyScriptSignature extends ScriptSignature {
  def signatures = List()
  def hex = ""
}

object ScriptSignature extends Factory[ScriptSignature] with BitcoinSLogger {


  /**
    * Returns an empty script signature
    *
    * @return
    */
  def empty : ScriptSignature = EmptyScriptSignature

  def fromBytes(bytes : Seq[Byte]) : ScriptSignature = RawScriptSignatureParser.read(bytes)

  /**
    * Creates a scriptSignature from the list of script tokens
    *
    * @param tokens
    * @return
    */
  def fromAsm(tokens : Seq[ScriptToken]) : ScriptSignature = tokens match {
    case Nil => EmptyScriptSignature
    case _  if (tokens.size > 1 && P2SHScriptSignature.isRedeemScript(tokens.last)) =>
      P2SHScriptSignature.fromAsm(tokens)
    case _ if (MultiSignatureScriptSignature.isMultiSignatureScriptSignature(tokens)) =>
      MultiSignatureScriptSignature.fromAsm(tokens)
    case List(w : BytesToPushOntoStack, x : ScriptConstant, y : BytesToPushOntoStack,
    z : ScriptConstant) => P2PKHScriptSignature.fromAsm(tokens)
    case List(w : BytesToPushOntoStack, x : ScriptConstant) => P2PKScriptSignature.fromAsm(tokens)
    case _ => NonStandardScriptSignature.fromAsm(tokens)
  }


  /**
    * Creates a script signature from the given tokens and scriptPubKey
    *
    * @param tokens the script signature's tokens
    * @param scriptPubKey the scriptPubKey which the script signature is trying to spend
    * @return
    */
  def fromScriptPubKey(tokens : Seq[ScriptToken], scriptPubKey : ScriptPubKey) : ScriptSignature = scriptPubKey match {
    case s : P2SHScriptPubKey => P2SHScriptSignature.fromAsm(tokens)
    case s : P2PKHScriptPubKey => P2PKHScriptSignature.fromAsm(tokens)
    case s : P2PKScriptPubKey => P2PKScriptSignature.fromAsm(tokens)
    case s : MultiSignatureScriptPubKey => MultiSignatureScriptSignature.fromAsm(tokens)
    case s : NonStandardScriptPubKey => NonStandardScriptSignature.fromAsm(tokens)
    case s : CLTVScriptPubKey => fromScriptPubKey(tokens, s.scriptPubKeyAfterCLTV)
    case s : CSVScriptPubKey => fromScriptPubKey(tokens, s.scriptPubKeyAfterCSV)
    case EmptyScriptPubKey => if (tokens.isEmpty) EmptyScriptSignature else NonStandardScriptSignature.fromAsm(tokens)
  }

  def apply(tokens : Seq[ScriptToken], scriptPubKey : ScriptPubKey) : ScriptSignature = fromScriptPubKey(tokens, scriptPubKey)
}

