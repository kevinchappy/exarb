import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
    private final static String path = "C:/Users/kevin/Desktop/test files";
    private static int bugCounter = 0;
    private static final Object bugCounterLock = new Object();
    private static int noBugCounter = 0;
    private static final Object noBugCounterLock = new Object();

    public static void main(String[] args) {

        File root = new File(path);
        List<File> javaFiles = getJavaFiles(root);

        if (!javaFiles.isEmpty()) {
            javaFiles.parallelStream().forEach(file -> {
                try {
                    Printer printer = new DefaultPrettyPrinter();
                    CompilationUnit cu = new JavaParser().parse(file).getResult().get();
                    cu.accept(new MethodVisitor(), null);
                    String modifiedCode = printer.print(cu);
                    Files.write(Paths.get(file.getAbsolutePath()), modifiedCode.getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            System.out.println("bug count: " + bugCounter);
            System.out.println("nobug count: " + noBugCounter);
            System.out.println("Total : " + (bugCounter + noBugCounter));
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

        for (File dir : directories){
            File[] fileList = dir.listFiles();
            if(fileList !=null)
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
                synchronized (noBugCounterLock){
                    noBugCounter++;
                }
            } else {
                Iterator<ForStmt> iter = list.iterator();
                while (true) {
                    ForStmt forStmt = iter.next();
                    if (!iter.hasNext() || rand.nextBoolean()) {
                        VariableDeclarationExpr varDeclExpr = (VariableDeclarationExpr) forStmt.getInitialization().get(0);
                        VariableDeclarator varDeclarator = varDeclExpr.getVariables().get(0);
                        IntegerLiteralExpr initialValue = (IntegerLiteralExpr) varDeclarator.getInitializer().get();
                        int newInitialValue = initialValue.asInt() + 1;
                        varDeclarator.setInitializer(new IntegerLiteralExpr(String.valueOf(newInitialValue)));
                        method.setName("bug");
                        synchronized (bugCounterLock){
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
                            BinaryExpr expr = (BinaryExpr) forStmt.getCompare().get();
                            if (forStmt.getInitialization().size() == 1 && (expr.getRight().isFieldAccessExpr())) {
                                list.add(forStmt);
                            }
                        }
                    }
                }
            }
            return list;
        }

    }


}



