import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.Printer;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FileVisitor {
    private final static String path = "";
    private static int bugCounter = 0;
    private static final Object bugCounterLock = new Object();
    private static int noBugCounter = 0;
    private static final Object noBugCounterLock = new Object();

    public static void main(String[] args) {

        System.out.print("Finding all files");
        List<File> javaFiles = extractJavaFiles(path);
        System.out.println();
        System.out.println("Found: " + javaFiles.size() + " files.");
        System.out.println("Beginning to mutate methods");

        if (!javaFiles.isEmpty()) {
            System.out.println("Starting timer");
            long start = System.nanoTime();
            javaFiles.parallelStream().forEach(file -> {
                try {
                    Printer printer = new DefaultPrettyPrinter();
                    CompilationUnit cu = new JavaParser().parse(file).getResult().orElse(null);
                    if (cu != null) {
                        cu.accept(new IfElseVisitor(), null);
                        String modifiedCode = printer.print(cu);
                        Files.write(Paths.get(file.getAbsolutePath()), modifiedCode.getBytes());

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            List<File> filesToDelete = new ArrayList<>();
            javaFiles.parallelStream().forEach(file -> {
                try {
                    CompilationUnit cu = new JavaParser().parse(file).getResult().orElse(null);
                    if (cu != null) {
                        if (cu.getTypes().stream().noneMatch(t -> t.getMethods().size() > 0)) {
                            filesToDelete.add(file);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            int oldCount = javaFiles.size();
            int deletedCounter = 0;
            int failedToDeleteCounter = 0;
            for (File file : filesToDelete) {
                if (file.delete()) {
                    deletedCounter++;
                } else
                    failedToDeleteCounter++;
            }


            long stop = System.nanoTime();

            System.out.println("Deleted " + deletedCounter + " files");
            System.out.println("Failed to delete " + failedToDeleteCounter + " files");
            System.out.println("Kept " + (oldCount - deletedCounter) + " Files");


            System.out.println("bug method count: " + bugCounter);
            System.out.println("nobug method count: " + noBugCounter);
            System.out.println("Total methods : " + (bugCounter + noBugCounter));
            System.out.println("Time taken: " + (stop - start) / 1.0E9);
        } else {
            System.out.println("Files not found");
        }
    }

    private static class MethodVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration method, Void arg) {
            Random rand = ThreadLocalRandom.current();
            List<ForStmt> list = getEligibleForStatements(method);
            if (list.isEmpty()) {
                return null;
            } else if (rand.nextBoolean()) {
                method.setName("nobug");
                synchronized (noBugCounterLock) {
                    noBugCounter++;
                }
            } else {
                ForStmt forStmt = list.get(rand.nextInt(list.size()));
                VariableDeclarationExpr varDeclExpr = null;
                for (Node node : forStmt.getInitialization()) {
                    if (node instanceof VariableDeclarationExpr) {
                        varDeclExpr = (VariableDeclarationExpr) node;
                    }
                }
                if (varDeclExpr == null) //remove method if it does not have a variable declaration, for example i = 0
                    return null;
                VariableDeclarator varDeclarator = varDeclExpr.getVariables().get(0);
                if (!(varDeclarator.getInitializer().isPresent() && varDeclarator.getInitializer().get().isIntegerLiteralExpr()))  //remove method if it does not use integer to initialize value
                    return null;

                IntegerLiteralExpr initialValue = (IntegerLiteralExpr) varDeclarator.getInitializer().get();
                int newInitialValue = initialValue.asInt() + 1;
                varDeclarator.setInitializer(new IntegerLiteralExpr(String.valueOf(newInitialValue)));
                method.setName("bug");
                synchronized (bugCounterLock) {
                    bugCounter++;
                }
            }
            return method;
        }

        private List<ForStmt> getEligibleForStatements(MethodDeclaration method) {
            List<ForStmt> list = new ArrayList<>();
            if (method.getBody().isPresent()) {
                for (Statement statement : method.getBody().get().getStatements()) {
                    if (statement.isForStmt()) {
                        ForStmt forStmt = statement.asForStmt();
                        if (forStmt.getCompare().isPresent()) {
                            Expression expr = forStmt.getCompare().get();
                            if (expr instanceof BinaryExpr) {
                                BinaryExpr bExpr = (BinaryExpr) expr;
                                if (forStmt.getInitialization().size() == 1 && (bExpr.getRight().isFieldAccessExpr())) {
                                    list.add(forStmt);
                                }
                            }
                        }
                    }
                }
            }
            return list;
        }
    }

    private static class IfElseVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration method, Void arg) {
            Random rand = ThreadLocalRandom.current();

            if (!hasEligibleIfStmts(method)) {
                return null;
            } else if (rand.nextBoolean()) {
                method.setName("nobug");
                synchronized ((noBugCounterLock)) {
                    noBugCounter++;
                }
            } else {
                getEligibleIfStmts(method);
                method.setName("bug");
                synchronized (bugCounterLock) {
                    bugCounter++;
                }
            }
            return method;
        }

        private boolean hasEligibleIfStmts(MethodDeclaration method) {
            if (method.getBody().isPresent()) {
                NodeList<Statement> body = method.clone().getBody().get().getStatements();
                for (Statement stmt : body) {
                    if (stmt.isIfStmt() && stmt.asIfStmt().hasCascadingIfStmt()) {
                        return true;
                    }

                }
            }
            return false;
        }

        private void getEligibleIfStmts(MethodDeclaration method) {
            Random rand = ThreadLocalRandom.current();

            if (method.getBody().isPresent()) {
                NodeList<Statement> body = method.getBody().get().getStatements();
                List<Integer> indexes = new ArrayList<>();
                for (Statement stmt : body) {
                    if (stmt.isIfStmt() && stmt.asIfStmt().hasCascadingIfStmt()) {
                        indexes.add( body.indexOf(stmt));
                    }
                }

                int index = indexes.get(rand.nextInt(indexes.size()));

                IfStmt orignalIfStmt = body.get(index).asIfStmt();
                List<IfStmt> list = getAllChildren(orignalIfStmt, new ArrayList<>());
                IfStmt newRoot = list.get(rand.nextInt(list.size()));

                Node parent = newRoot.getParentNode().get();

                IfStmt statement =  (IfStmt) parent;



                statement.removeElseStmt();
                body.add(index + 1, newRoot);
                method.setBody(method.getBody().get().setStatements(body));
            }
        }

        List<IfStmt> getAllChildren(IfStmt parent, List<IfStmt> list) {
            if (parent.getElseStmt().isPresent() && parent.getElseStmt().get().isIfStmt()) {
                IfStmt child = parent.getElseStmt().get().asIfStmt();
                list.add(child);
                return getAllChildren(child, list);
            }
            return list;
        }
    }

    private static List<File> extractJavaFiles(String directoryPath) {
        List<File> javaFiles = new ArrayList<>();
        File directory = new File(directoryPath);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(extractJavaFiles(file.getAbsolutePath()));
                    } else if (file.isFile() && file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
}



