import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForStmt;
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
    private final static String path = "C:/Users/kevin/Desktop/java-med";
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
                        cu.accept(new MethodVisitor(), null);
                        String modifiedCode = printer.print(cu);
                        Files.write(Paths.get(file.getAbsolutePath()), modifiedCode.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            long stop = System.nanoTime();

            System.out.println("bug count: " + bugCounter);
            System.out.println("nobug count: " + noBugCounter);
            System.out.println("Total : " + (bugCounter + noBugCounter));
            System.out.println("Time taken: " + (stop - start) / 1.0E9);
        } else {
            System.out.println("Files not found");
        }
    }

    private static List<File> getJavaFiles(File directory) {
        File[] directoryList = directory.listFiles();

        ArrayList<File> directories = new ArrayList<>();
        ArrayList<File> list = new ArrayList<>();

        if (directoryList != null) {
            Collections.addAll(directories, directoryList);
        }

        for (File dir : directories) {
            File[] fileList = dir.listFiles();
            if (fileList != null)
                Collections.addAll(list, fileList);
        }
        return list;
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
                Iterator<ForStmt> iter = list.iterator();
                while (true) {
                    ForStmt forStmt = iter.next();
                    if (!iter.hasNext() || rand.nextBoolean()) {
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
                        break;
                    }
                }
            }
            return method;
        }


        private List<ForStmt> getEligibleForStatements(MethodDeclaration method) {
            List<ForStmt> list = new ArrayList<>();
            if (method.getBody().isPresent()) {
                for (Statement statement : method.getBody().get().getStatements()) {
                    if (statement instanceof ForStmt) {
                        ForStmt forStmt = (ForStmt) statement;
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



