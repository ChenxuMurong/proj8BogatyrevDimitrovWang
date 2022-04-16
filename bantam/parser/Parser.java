/*
 * Authors: Haoyu Song and Dale Skrien
 * Latest change: Oct. 5, 2021
 *
 * In the grammar below, the variables are enclosed in angle brackets and
 * "::=" is used instead of "-->" to separate a variable from its rules.
 * The special character "|" is used to separate the rules for each variable
 * (but note that "||" is an operator).
 * EMPTY indicates a rule with an empty right hand side.
 * All other symbols in the rules are terminals.
 */
package proj8BogatyrevDimitrovWang.bantam.parser;


import proj8BogatyrevDimitrovWang.bantam.lexer.Scanner;
import proj8BogatyrevDimitrovWang.bantam.lexer.Token;
import proj8BogatyrevDimitrovWang.bantam.util.CompilationException;
import proj8BogatyrevDimitrovWang.bantam.util.Error;
import proj8BogatyrevDimitrovWang.bantam.util.ErrorHandler;
import proj8BogatyrevDimitrovWang.bantam.ast.*;

import java.io.IOException;
import java.util.Set;

import static proj8BogatyrevDimitrovWang.bantam.lexer.Token.Kind.*;


public class Parser
{
    // instance variables
    private Scanner scanner; // provides the tokens
    private String fileName; // stores the filename
    private Token currentToken; // the lookahead token
    private ErrorHandler errorHandler; // collects & organizes the error messages

    // constructor
    public Parser(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }


    /** helper function. Registers Parse error on
     * error handler and throws compilation exception
     * @author Baron Wang
     * @param message error message to show
     * @throws CompilationException to be caught in main
     */
    private void handleErr(String message) throws CompilationException{
        errorHandler.register(Error.Kind.PARSE_ERROR, message);
        throw new CompilationException(errorHandler);
    }


    /**
     * parse the given file and return the root node of the AST
     * @param filename The name of the Bantam Java file to be parsed
     * @return The Program node forming the root of the AST generated by the parser
     */
    public Program parse(String filename) throws IOException {
        errorHandler = new ErrorHandler();
        scanner = new Scanner(filename, errorHandler);
        fileName = filename;
        return parseProgram();
    }

    // <Program> ::= <Class> | <Class> <Program>
    private Program parseProgram() throws IOException {
        int position = currentToken.position;
        ClassList clist = new ClassList(position);

        while (currentToken.kind != EOF) {
            Class_ aClass = parseClass();
            clist.addElement(aClass);
        }

        return new Program(position, clist);
    }


    // <Class> ::= CLASS <Identifier> <ExtendsClause> { <MemberList> }
    // <ExtendsClause> ::= EXTENDS <Identifier> | EMPTY
    // <MemberList> ::= EMPTY | <Member> <MemberList>
    private Class_ parseClass() throws IOException {
        int position = currentToken.position;
        if(currentToken.kind != Token.Kind.CLASS){
            handleErr("Exception: expecting a \"class\" keyword");
        }
        currentToken = scanner.scan();
        String identifier = parseIdentifier();
        String parentIdentifier = null;
        if (currentToken.spelling.equals("extends")){
            currentToken = scanner.scan();
            parentIdentifier = parseIdentifier();
        }
        currentToken = scanner.scan();
        MemberList memberList = new MemberList(position);
        // while currentToken is not hitting "}", parse next member
        while (!currentToken.spelling.equals("}")){
            Member currentMember = parseMember();
            memberList.addElement(currentMember);
        }
        return new Class_(position, fileName, identifier, parentIdentifier, memberList);

    }


    //Fields and Methods
    // <Member> ::= <Field> | <Method>
    // <Method> ::= <Type> <Identifier> ( <Parameters> ) <BlockStmt>
    // <Field> ::= <Type> <Identifier> <InitialValue> ;
    // <InitialValue> ::= EMPTY | = <Expression>
    private Member parseMember() throws IOException {
        int position = currentToken.position;
        // either way, starts with Type and Identifier
        String typeName = parseType();
        String funcOrVarName = parseIdentifier();

        // case 1: field
        if (currentToken.spelling.equals("=")){
            currentToken = scanner.scan();
            Expr expr = parseExpression();
            return new Field(position,typeName,funcOrVarName,expr);
        }
            // field without initialization
        if (currentToken.spelling.equals(";")){
            currentToken = scanner.scan();
            // init is an "optional" field so i'm passing null
            return new Field(position,typeName,funcOrVarName,null);
        }

        // case 2: method
        if (currentToken.spelling.equals("(")){
            currentToken = scanner.scan();
            FormalList formalList = parseParameters();
            // now it should get a ')'. If not, error out
            if(!currentToken.spelling.equals(")")){
                handleErr("Illegal method declaration. \")\" expected");
            }
            currentToken = scanner.scan();
            // parseBlock should only return BlockStmt
            // TODO potential bug here with BlockStmt
            BlockStmt blockStmt = (BlockStmt) parseBlock();
            // return this method
            return new Method(position,typeName,
                    funcOrVarName,formalList, blockStmt.getStmtList());
        }

        // program shouldn't reach here; if it does, it got a wrong token
        handleErr("Illegal field/method declaration. " +
                "\"(\", \"=\", or \";\" expected");
        // this return statement will never be reached.
        // it is here to keep IntelliJ happy
        return null;
    }


    //-----------------------------------
    //Statements
    // <Stmt> ::= <WhileStmt> | <ReturnStmt> | <BreakStmt> | <VarDeclaration>
    //             | <ExpressionStmt> | <ForStmt> | <BlockStmt> | <IfStmt>
    private Stmt parseStatement() throws IOException {
            Stmt stmt;

            switch (currentToken.kind) {
                case IF:
                    stmt = parseIf();
                    break;
                case LCURLY: 
                    stmt = parseBlock();
                    break;
                case VAR: 
                    stmt = parseVarDeclaration();
                    break;
                case RETURN: 
                    stmt = parseReturn();
                    break;
                case FOR: 
                    stmt = parseFor();
                    break;
                case WHILE: 
                    stmt = parseWhile();
                    break;
                case BREAK: 
                    stmt = parseBreak();
                    break;
                default: 
                    stmt = parseExpressionStmt();
            }

            return stmt;
    }


    // <WhileStmt> ::= WHILE ( <Expression> ) <Stmt>
    private Stmt parseWhile() throws IOException {
        int position = currentToken.position;
        Expr predExpr;
        Stmt stmt;
        // moving on from token WHILE
        currentToken = scanner.scan();
        // check for "("
        if (!currentToken.spelling.equals("(")){
            handleErr("Illegal while statement: " +
                    "missing conditions, \"(\" expected");
        }
        currentToken = scanner.scan();
        predExpr = parseExpression();
        // check for ")"
        if (!currentToken.spelling.equals(")")){
            handleErr("Illegal while statement: " +
                    "unclosed parenthesis, \")\" expected");
        }
        currentToken = scanner.scan();
        stmt = parseStatement();
        return new WhileStmt(position,predExpr,stmt);
    }


    // <ReturnStmt> ::= RETURN <Expression> ; | RETURN ;
    private Stmt parseReturn() throws IOException {
        /* inspired from the code shown in class on Tuesday */
         Expr expr = null;
        Token currentToken = scanner.scan();

         if (currentToken.kind != SEMICOLON){
             expr = parseExpression();
             if (currentToken.kind != SEMICOLON){
                 handleErr("Illegal return statement: " +
                         "\";\" expected");
               }
        }
        currentToken = scanner.scan();

        return new ReturnStmt(currentToken.position, expr);
    }


    // <BreakStmt> ::= BREAK ;
    private Stmt parseBreak() {
        // current token should be pointing to ";"
        if (currentToken.kind != SEMICOLON){
            handleErr("Illegal break statement: " +
                    "\";\" expected");
        }
        return new BreakStmt(currentToken.position);
    }


    // <ExpressionStmt> ::= <Expression> ;
    private ExprStmt parseExpressionStmt() throws IOException {
        return new ExprStmt(
                currentToken.position, parseExpression()) ;
    }


    // <VarDeclaration> ::= VAR <Id> = <Expression> ;
    // Every local variable must be initialized
    private Stmt parseVarDeclaration() throws IOException {
        int position = currentToken.position;
        String name = "";
        Expr expr;
        // get next token which should be an identifier
        currentToken = scanner.scan();
        if (currentToken.kind != IDENTIFIER){
            handleErr("Illegal var declaration statement: " +
                    "var must be initialized");
        }
        name = currentToken.getSpelling();
        currentToken = scanner.scan();

        if (currentToken.kind != ASSIGN){
            handleErr("Illegal var declaration statement: " +
                    "expecting an identifier");
        }
        currentToken = scanner.scan();

        expr = parseExpression();
        if (currentToken.kind != SEMICOLON){
                handleErr("Illegal var declaration " +
                        "statement: missing semicolon");
        }

        // always move the token forward by one
        currentToken = scanner.scan();

        return new DeclStmt(position, name, expr);
    }


    // <ForStmt> ::= FOR ( <Start> ; <Terminate> ; <Increment> ) <STMT>
    // <Start> ::=     EMPTY | <Expression>
    // <Terminate> ::= EMPTY | <Expression>
    // <Increment> ::= EMPTY | <Expression>
    private Stmt parseFor() throws IOException {
        int position = currentToken.position;
        Expr startExpr = null;
        Expr endExpr = null;
        Expr updateExpr = null;
        Stmt bodyStmt;

        // moving on from token FOR
        currentToken = scanner.scan();
        // check for "("
        if (!currentToken.spelling.equals("(")){
            handleErr("Illegal for statement: " +
                    "missing parenthesis, \"(\" expected");
        }

        currentToken = scanner.scan();
        // if start isn't empty, parse it
        if (!currentToken.spelling.equals(";")){
            startExpr = parseExpression();
        }
        // at this point currentToken is ";"
        currentToken = scanner.scan();
        // if end condition isn't empty, parse it
        if (!currentToken.spelling.equals(";")){
            endExpr = parseExpression();
        }
        // at this point currentToken is ";"
        currentToken = scanner.scan();
        // if start isn't empty, parse it
        if (!currentToken.spelling.equals(";")){
            updateExpr = parseExpression();
        }
        // at this point currentToken is ";"
        currentToken = scanner.scan();

        // check for ")"
        if (!currentToken.spelling.equals(")")){
            handleErr("Illegal for statement: " +
                    "unclosed parenthesis, \")\" expected");
        }
        currentToken = scanner.scan();
        bodyStmt = parseStatement();
        return new ForStmt(position,startExpr,endExpr,updateExpr,bodyStmt);
    }


    // <BlockStmt> ::= { <Body> }
    // <Body> ::= EMPTY | <Stmt> <Body>
    private Stmt parseBlock() throws IOException {
        int position = currentToken.position;
        // moving on from token {
        currentToken = scanner.scan();
        StmtList stmtList = new StmtList(position);
        // adds statements into the statement list
        // until currentToken reaches "}"
        while(!currentToken.spelling.equals("}")){
            Stmt stmt = parseStatement();
            stmtList.addElement(stmt);
        }
        return new BlockStmt(position,stmtList);
    }


    // <IfStmt> ::= IF ( <Expr> ) <Stmt> | IF ( <Expr> ) <Stmt> ELSE <Stmt>
    private Stmt parseIf() throws IOException {
        int position = currentToken.position;
        Expr predExpr;
        Stmt bodyStmt;
        Stmt elseStmt = null;
        // moving on from token IF
        currentToken = scanner.scan();
        // check for "("
        if (!currentToken.spelling.equals("(")){
            handleErr("Illegal if statement: " +
                    "missing conditions, \"(\" expected");
        }
        currentToken = scanner.scan();
        predExpr = parseExpression();
        // check for ")"
        if (!currentToken.spelling.equals(")")){
            handleErr("Illegal if statement: " +
                    "unclosed parenthesis, \")\" expected");
        }
        currentToken = scanner.scan();
        bodyStmt = parseStatement();

        // checking for ELSE
        if (currentToken.spelling.equals("else")){
            currentToken = scanner.scan();
            elseStmt = parseStatement();
        }
        
        return new IfStmt(position,predExpr,bodyStmt,elseStmt);
    }


    //-----------------------------------------
    // Expressions
    // Here we use different rules than the grammar on page 49
    // of the manual to handle the precedence of operations

    // <Expression> ::= <LogicalORExpr> <OptionalAssignment>
    // <OptionalAssignment> ::= EMPTY | = <Expression>
    // most assignments should be processed here
    private Expr parseExpression() throws IOException {
        int position = currentToken.position;
        Expr expr = parseOrExpr();
        /*  Check whether the currentToken has type ASSIGN and
        check whether expr is an instance of VarExpr. */
        if (currentToken.kind == ASSIGN && expr instanceof VarExpr){
            currentToken = scanner.scan();

            Expr rightExpr = parseExpression();
            return new AssignExpr(position,
                    // refName could be null, this, or super
                    // we get it from the ref field of VarExpr
                    ((VarExpr) ((VarExpr) expr).getRef()).getName(),
                    ((VarExpr) expr).getName(),rightExpr);
        }
        return expr;
    }


    // <LogicalOR> ::= <logicalAND> <LogicalORRest>
    // <LogicalORRest> ::= EMPTY |  || <LogicalAND> <LogicalORRest>
    // errors should be relayed to parseAndExpr
    private Expr parseOrExpr() throws IOException {
        int position = currentToken.position;
        Expr left;

        left = parseAndExpr();
        while (currentToken.spelling.equals("||")) {
            currentToken = scanner.scan();

            Expr right = parseAndExpr();
            left = new BinaryLogicOrExpr(position, left, right);
        }

        return left;
    }


    // <LogicalAND> ::= <ComparisonExpr> <LogicalANDRest>
    // <LogicalANDRest> ::= EMPTY |  && <ComparisonExpr> <LogicalANDRest>
    // errors should be relayed to parseEqualityExpr
    private Expr parseAndExpr() throws IOException {
        int position = currentToken.position;
        // parseEqualityExpr() contains ComparisonExpr
        Expr left = parseEqualityExpr();
        // currentToken at LogicalAndRest
        while (currentToken.spelling.equals("&&")){
            currentToken = scanner.scan();

            Expr right = parseEqualityExpr();
            left = new BinaryLogicAndExpr(position,left,right);
        }
        return left;
    }


    // <ComparisonExpr> ::= <RelationalExpr> <equalOrNotEqual> <RelationalExpr> |
    //                      <RelationalExpr>
    // <equalOrNotEqual> ::=  == | !=
    private Expr parseEqualityExpr() throws IOException {
        int position = currentToken.position;
        Expr leftRelExpr = parseRelationalExpr();
        // if current token is <equalOrNotEqual>
        if (currentToken.spelling.equals("==")){
            Expr rightRelExpr = parseRelationalExpr();
            leftRelExpr = new BinaryCompEqExpr(position,
                    leftRelExpr, rightRelExpr);
        }
        else if (currentToken.spelling.equals("!=")){
            Expr rightRelExpr = parseRelationalExpr();
            leftRelExpr = new BinaryCompNeExpr(position,
                    leftRelExpr, rightRelExpr);
        }
        return leftRelExpr;
    }


    // <RelationalExpr> ::= <AddExpr> | <AddExpr> <ComparisonOp> <AddExpr>
    // <ComparisonOp> ::= < | > | <= | >=
    private Expr parseRelationalExpr() throws IOException {
        int position = currentToken.position;
        Expr leftExpr = parseAddExpr();
        Expr rightExpr;
        // check if current token is comparison op
        switch (currentToken.spelling){
            case "<":
                currentToken = scanner.scan();
                rightExpr = parseAddExpr();
                return new BinaryCompLtExpr(position,
                        leftExpr,rightExpr);
            case ">":
                currentToken = scanner.scan();
                rightExpr = parseAddExpr();
                return new BinaryCompGtExpr(position,
                        leftExpr,rightExpr);
            case "<=":
                currentToken = scanner.scan();
                rightExpr = parseAddExpr();
                return new BinaryCompLeqExpr(position,
                        leftExpr,rightExpr);
            case ">=":
                currentToken = scanner.scan();
                rightExpr = parseAddExpr();
                return new BinaryCompGeqExpr(position,
                        leftExpr,rightExpr);
            default:
                // default is when current token isn't
                // comparison op, so it terminates
                // the function
                return leftExpr;
        }
    }


    // <AddExpr>::＝ <MultExpr> <MoreMultExpr>
    // <MoreMultExpr> ::= EMPTY | + <MultExpr> <MoreMultExpr> | - <MultExpr> <MoreMultExpr>
    private Expr parseAddExpr() throws IOException {
        int position = currentToken.position;
        Expr expr = parseMultExpr();
        // checks if token is + or -
        while(currentToken.spelling.equals("+")
           || currentToken.spelling.equals("-")){
            currentToken = scanner.scan();
            // make the right hand side another expr
            Expr anotherExpr = parseMultExpr();
            // combine right hand side expr with (left hand side) expr
            expr = (currentToken.spelling.equals("+") ?
                new BinaryArithPlusExpr(position, expr, anotherExpr) :
                new BinaryArithMinusExpr(position, expr, anotherExpr));
            // repeat until not + or - anymore
        }
        return expr;
    }


    // <MultiExpr> ::= <NewCastOrUnary> <MoreNCU>
    // <MoreNCU> ::= * <NewCastOrUnary> <MoreNCU> |
    //               / <NewCastOrUnary> <MoreNCU> |
    //               % <NewCastOrUnary> <MoreNCU> |
    //               EMPTY
    private Expr parseMultExpr() throws IOException {
        int position = currentToken.position;
        Expr expr = parseNewCastOrUnary();
        // checks if token is *, / or %
        Expr rightExpr;
        while(currentToken.spelling.equals("*")
        || currentToken.spelling.equals("/")
        || currentToken.spelling.equals("%")){
            switch (currentToken.spelling) {
                case "*" -> {
                    currentToken = scanner.scan();
                    // make the right hand side another expr
                    rightExpr = parseNewCastOrUnary();
                    // combine right hand side expr with
                    // (left hand side) expr
                    expr = new BinaryArithTimesExpr(position,
                            expr, rightExpr);
                }
                case "/" -> {
                    currentToken = scanner.scan();
                    rightExpr = parseNewCastOrUnary();
                    expr = new BinaryArithDivideExpr(position,
                            expr, rightExpr);
                }
                case "%" -> {
                    currentToken = scanner.scan();
                    rightExpr = parseNewCastOrUnary();
                    expr = new BinaryArithModulusExpr(position,
                            expr, rightExpr);
                }
                // don't need a default case because
                // the switch statement has the same condition
                // as the while loop. It will just break out of
                // the while loop once it is finished
            }
        }
        return expr;

    }

    // <NewCastOrUnary> ::= <NewExpression> | <CastExpression> | <UnaryPrefix>
    // make the three expressions return NULL whenever the first token
    // doesn't match TODO refactor
    private Expr parseNewCastOrUnary() throws IOException {
        Expr expr;
       // if (parseNew() == null){
        Expr e = parseNew();
        if(e != null){
            return e;
        }
        e = parseCast();
        if(e != null){
            return e;
        }
        return parseUnaryPrefix();


    }


    // <NewExpression> ::= NEW <Identifier> ( )
    private Expr parseNew() throws IOException {
        int position = currentToken.position;

        if (currentToken.kind != NEW){
            // if token isn't NEW, return null
            // other functions may return real val
            return null;
        }
        currentToken = scanner.scan();
        String typeStr = parseIdentifier();
        // check for paren
        if (currentToken.kind != LPAREN){
            handleErr("Exception: expecting a \"(\"");
        }
        currentToken = scanner.scan();
        if (currentToken.kind != RPAREN){
            handleErr("Exception: expecting a \")\"");
        }
        // if it reached this point, return expression
        return new NewExpr(position, typeStr);
    }


    // <CastExpression> ::= CAST ( <Type> , <Expression> )
    private Expr parseCast() throws IOException {
        int position = currentToken.position;

        if (currentToken.kind != CAST){
            // if token isn't CAST, return null
            // other functions may return real val
            return null;
        }
        currentToken = scanner.scan();
        if (currentToken.kind != LPAREN){
            handleErr("Exception: expecting a \"(\"");
        }
        currentToken = scanner.scan();
        String typeStr = parseType();
        Expr exprStr = parseExpression();
        if (currentToken.kind != RPAREN){
            handleErr("Exception: expecting a \")\"");
        }

        return new CastExpr(position,typeStr,exprStr);

    }


    // <UnaryPrefix> ::= <PrefixOp> <UnaryPreFix> | <UnaryPostfix>
    // <PrefixOp> ::= - | ! | ++ | --
    private Expr parseUnaryPrefix() throws IOException {
        // ! this function never returns null
        int position = currentToken.position;

        // decide if the token corresponds to PrefixOp or UnaryPostfix
        if(!currentToken.spelling.equals("-") &&  !currentToken.spelling.equals("!")
         &&!currentToken.spelling.equals("++") && !currentToken.spelling.equals("--")){
            // should be UnaryPostFix if not prefix.
            // Error handling should be relayed to parseUnaryPostfix()

            return parseUnaryPostfix();
        };

        // if it is a prefixOp then it switches cases
        // advances to next token and recurse

        switch (currentToken.spelling) {
            case "-" -> {
                currentToken = scanner.scan();
                return new UnaryNegExpr(position,
                        parseUnaryPrefix());
            }
            case "!" -> {
                currentToken = scanner.scan();
                return new UnaryNotExpr(position,
                        parseUnaryPrefix());
            }
            case "++" -> {
                currentToken = scanner.scan();
                return new UnaryIncrExpr(position,
                        parseUnaryPrefix(), false);
            }
            case "--" -> {
                currentToken = scanner.scan();
                return new UnaryDecrExpr(position,
                        parseUnaryPrefix(), false);
            }
            // no need for default because all possibilities
            // have been covered
        }
        // this line should never be reached
        // TODO delete debugging line
        System.out.println("ERROR this line should never be reached");
        return null;
    }


    // <UnaryPostfix> ::= <Primary> <PostfixOp>
    // <PostfixOp> ::= ++ | -- | EMPTY
    private Expr parseUnaryPostfix() throws IOException {
        int position = currentToken.position;
        Expr primaryExpr = parsePrimary();

        // check for post++
        if (currentToken.spelling.equals("++")){
            currentToken = scanner.scan();
            return new UnaryIncrExpr(position,primaryExpr,true);

        }
        // check for post--
        else if (currentToken.spelling.equals("--")){
            currentToken = scanner.scan();
            return new UnaryDecrExpr(position,primaryExpr,true);
        }
        // no postfix
        else{
            return primaryExpr;
        }
    }


    // <Primary> ::= ( <Expression> ) | <IntegerConst> | <BooleanConst> |
    //                              <StringConst> | <VarExpr>
    // <VarExpr> ::= <VarExprPrefix> <Identifier> <VarExprSuffix>
    // <VarExprPrefix> ::= SUPER . | THIS . | EMPTY
    // <VarExprSuffix> ::= ( <Arguments> ) | EMPTY
    private Expr parsePrimary() throws IOException {
        int position = currentToken.position;
        Expr expr;
        // moving on from token (
        currentToken = scanner.scan();
        // case 1: ( <Expression> )
        // check for "("
        if (currentToken.spelling.equals("(")){
            currentToken = scanner.scan();
            expr = parseExpression();
            // check for ")"
            if (!currentToken.spelling.equals(")")){
                handleErr("Illegal expression: " +
                        "unclosed parenthesis, \")\" expected");
            }
            return expr;
        }
        // case 2: <IntegerConst>
        else if(currentToken.kind == INTCONST){
            return parseIntConst();
        }
        // case 3: <BooleanConst>
        else if(currentToken.kind == BOOLEAN){
            return parseBoolean();
        }
        // case 4: <StringConst>
        else if(currentToken.kind == STRCONST){
            return parseStringConst();
        }
        // case 5: varExpr
        else {
            VarExpr prefixVarExpr;
            String name; // id

            // switch statement checking for "super."/"this."
            switch (currentToken.spelling) {
                // "this."  appearing first
                case "this" -> {
                    prefixVarExpr = new VarExpr(position, null, "this");
                    currentToken = scanner.scan();
                    // next token should be DOT
                    if (currentToken.kind != DOT) {
                        handleErr("Illegal variable expression: " +
                                "dot (\".\") must come after \"this\"");
                    }
                    currentToken = scanner.scan();
                    // after DOT it should be identifier
                }
                // "this."  appearing first
                case "super" -> {
                    prefixVarExpr = new VarExpr(position, null, "super");
                    currentToken = scanner.scan();
                    // next token should be DOT
                    if (currentToken.kind != DOT) {
                        handleErr("Illegal variable expression: " +
                                "dot (\".\") must come after \"super\"");
                    }
                    currentToken = scanner.scan();
                    // after DOT it should be identifier
                }
                // no "super" or "this"
                default -> {
                    prefixVarExpr = null;
                    currentToken = scanner.scan();
                    // next it should be identifier
                }
            }
            name = parseIdentifier();

            // search for (<Arguments>)
            if (currentToken.spelling.equals("(")){
                currentToken = scanner.scan();
                ExprList args = parseArguments();
                // check for closing paren
                if (!currentToken.spelling.equals(")")){
                    handleErr("Illegal expression: " +
                            "unclosed parenthesis, \")\" expected");
                }
                currentToken = scanner.scan();
                // it's a method call (dispatch) if it hit "("
                return new DispatchExpr(position,prefixVarExpr,name,args);
            }
            // non-dispatch call
            return new VarExpr(position, prefixVarExpr, name);
        }

    }


    // <Arguments> ::= EMPTY | <Expression> <MoreArgs>
    // <MoreArgs>  ::= EMPTY | , <Expression> <MoreArgs>
    private ExprList parseArguments() throws IOException {
        int position = currentToken.position;
        ExprList exprList = new ExprList(position);
        // since parsePrimary() is the only one that calls this
        // function, we know that as long as !currentToken.equals(")")
        // it is reading an Expression.
        if (!currentToken.spelling.equals(")")) {

            Expr expr = parseExpression();
            exprList.addElement(expr);

            // can't have expr after expr.
            // token MUST BE EITHER ")" OR ","

            // don't need to do anything if it's ")"
            // if it's a "," skip it and read next expression
            while(currentToken.kind == COMMA){
                currentToken = scanner.scan();
                Expr expr2 = parseExpression();
                exprList.addElement(expr2);
            }

        }
        // currentToken should be ")" at this point
        // potential error handled in caller function
        return exprList;
    }


    // <Parameters> ::=  EMPTY | <Formal> <MoreFormals>
    // <MoreFormals> ::= EMPTY | , <Formal> <MoreFormals
    private FormalList parseParameters() throws IOException {
        int position = currentToken.position;
        FormalList formalList = new FormalList(position);
        // the only function that calls parseParams() is parseMember()
        // when it is trying to parse a method. So the ending condition
        // should be ")"

        if (!currentToken.spelling.equals(")")) {

            Formal formal = parseFormal();
            formalList.addElement(formal);

            // can't have formal after formal.
            // token MUST BE EITHER ")" OR ","

            // don't need to do anything if it's ")"
            // if it's a "," skip it and read next formal
            while(currentToken.kind == COMMA){
                currentToken = scanner.scan();
                Formal formal2 = parseFormal();
                formalList.addElement(formal2);
            }

        }
        // currentToken should be ")" at this point
        // potential error handled in caller function
        return formalList;

    }


    // <Formal> ::= <Type> <Identifier>
    private Formal parseFormal() {
        int position = currentToken.position;
        // return null if parseType fails
        String typeName = parseType();
        String idName = parseIdentifier();
        return new Formal(position,typeName,idName);
    }


    // <Type> ::= <Identifier>
    private String parseType() {
        return parseIdentifier();
    }


    //----------------------------------------
    //Terminals

    private String parseOperator() {
        // set of all possible operators
        Set<Token.Kind> operators = Set.of(BINARYLOGIC, PLUSMINUS,
                MULDIV, COMPARE, UNARYINCR, UNARYDECR, ASSIGN,
                UNARYNOT);
        if (!operators.contains(currentToken.kind)){
            handleErr("Illegal identifier");
        }
        return currentToken.spelling;
    }


    private String parseIdentifier() {
        if (currentToken.kind != IDENTIFIER){
            handleErr("Illegal identifier");
        }
        return currentToken.spelling;
    }


    private ConstStringExpr parseStringConst() throws IOException {
        if (currentToken.kind != STRCONST){
            handleErr("Illegal string constant");
        }
        int position = currentToken.position;
        //...save the currentToken's string to a local variable...
        String strConst = currentToken.spelling;
        //...advance to the next token...
        currentToken = scanner.scan();
        //...return a new ConstStringExpr containing the string...
        return new ConstStringExpr(position,strConst);
    }


    private ConstIntExpr parseIntConst() {
        if (currentToken.kind != INTCONST){
            handleErr("Illegal integer constant");
        }
        int position = currentToken.position;
        return new ConstIntExpr(position, currentToken.spelling);
    }


    private ConstBooleanExpr parseBoolean() {
        if (currentToken.kind != BOOLEAN){
            handleErr("Illegal boolean");
        }
        int position = currentToken.position;
        return new ConstBooleanExpr(position, currentToken.spelling);
    }

}

