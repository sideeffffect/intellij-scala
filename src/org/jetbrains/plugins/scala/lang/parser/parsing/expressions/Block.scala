package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.base._
import org.jetbrains.plugins.scala.lang.parser.parsing.top._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 11:34:28
* To change this template use File | Settings | File Templates.
*/

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */

//TODO: fix this bad style
object Block {
  def parse(builder: PsiBuilder): Boolean = {
    val blockMarker = builder.mark
    var exit = true
    var rollbackMarker = builder.mark
    while (BlockStat.parse(builder) && true) {
      var exit2 = true
      while (exit2) {
        var flag = false
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer //Ate semi
            flag = true
          }
          case _ => {
            exit2 = false
            if (!flag) {
              exit = false
            }
          }
        }
      }
      if (exit) {
        rollbackMarker.drop
        rollbackMarker = builder.mark
      }
    }
    if (!exit) rollbackMarker.rollbackTo
    else rollbackMarker.drop
    ResultExpr parse builder
    blockMarker.done(ScalaElementTypes.BLOCK)
    return true
  }
  def parse(builder: PsiBuilder, hasBrace: Boolean) : Boolean = {
    if (hasBrace) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE => {
          builder.advanceLexer
        }
        case _ => {
          return false
        }
      }
      parse(builder)
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE => {
          builder.advanceLexer
        }
        case _ => {
          builder error ScalaBundle.message("rbrace.expected", new Array[Object](0))
        }
      }
    }
    else {
      parse(builder)
    }
    return true
  }
}