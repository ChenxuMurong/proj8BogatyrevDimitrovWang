/* Bantam Java Compiler and Language Toolset.

   Copyright (C) 2009 by Marc Corliss (corliss@hws.edu) and 
                         David Furcy (furcyd@uwosh.edu) and
                         E Christopher Lewis (lewis@vmware.com).
   ALL RIGHTS RESERVED.

   The Bantam Java toolset is distributed under the following 
   conditions:

     You may make copies of the toolset for your own use and 
     modify those copies.

     All copies of the toolset must retain the author names and 
     copyright notice.

     You may not sell the toolset or distribute it in 
     conjunction with a commerical product or service without 
     the expressed written consent of the authors.

   THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS 
   OR IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE 
   IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
   PARTICULAR PURPOSE. 
*/

package proj8BogatyrevDimitrovWang.bantam.ast;

import proj8BogatyrevDimitrovWang.bantam.visitor.Visitor;


/**
 * The <tt>ConstStringExpr</tt> class represents a String constant expression.
 * It extends constant expressions so it contains a constant value (represented
 * as a String).  Since this class is similar to other subclasses most of the
 * functionality can be implemented in the bantam.visitor method for the parent class.
 *
 * @see ASTNode
 * @see ConstExpr
 */
public class ConstStringExpr extends ConstExpr {
    /**
     * ConstStringExpr constructor
     *
     * @param lineNum  source line number corresponding to this AST node
     * @param constant constant value (as a String)
     */
    public ConstStringExpr(int lineNum, String constant) {
        super(lineNum, constant);
    }

    /**
     * Visitor method
     *
     * @param v bantam.visitor object
     * @return result of visiting this node
     * @see proj8BogatyrevDimitrovWang.bantam.visitor.Visitor
     */
    public Object accept(Visitor v) {
        return v.visit(this);
    }
}
