import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.*;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;

import java.util.List;

public class MvpCreate extends BaseGenerateAction {
    private static final String UP_ACTIVITY = "Activity";
    private static final String LOW_ACTIVITY = "activity";
    private static final String UP_FRAGMENT = "Fragment";
    private static final String LOW_FRAGMENT = "fragment";
    private static final String UP_VIEW = "View";
    private static final String LOW_VIEW = "view";
    private static final String UP_MODEL = "Model";
    private static final String LOW_MODEL = "model";
    private static final String UP_PRESENTER = "Presenter";
    private static final String LOW_PRESENTER = "presenter";
    private Editor mEditor;
    private Project mProject;
    private PsiJavaFile mActivityFile;
    private JavaDirectoryService mDirectoryService;
    private String mName;
    private PsiElementFactory mElementFactory;
    private PsiShortNamesCache mNamesCache;
    private PsiFileFactory mPsiFileFactory;
    private JavaCodeStyleManager mStyleManager;
    private boolean mIsActivity;
    private PsiClass mTargetClass;
    private PsiElement mSemicolon;
    private PsiElement mWhiteSpace;

    public MvpCreate() {
        super(null);
    }

    public MvpCreate(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiJavaFile psiFile = ((PsiJavaFile) e.getData(LangDataKeys.PSI_FILE));
        Presentation presentation = e.getPresentation();
        if (psiFile != null && editor != null) {
            PsiClass targetClass = getTargetClass(editor, psiFile);
            presentation.setEnabledAndVisible(isActivity(targetClass) || isFragment(targetClass));
            return;
        }
        presentation.setEnabledAndVisible(false);
    }

    /**
     * 是否为Activity
     *
     * @param psiClass
     * @return
     */
    private boolean isActivity(PsiClass psiClass) {
        if (psiClass != null) {
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                String name = superClass.getName();
                return UP_ACTIVITY.equals(name) || isActivity(superClass);
            }
        }
        return false;
    }

    /**
     * 是否为Fragment
     *
     * @param psiClass
     * @return
     */
    private boolean isFragment(PsiClass psiClass) {
        if (psiClass != null) {
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                String name = superClass.getName();
                return UP_FRAGMENT.equals(name) || isFragment(superClass);
            }
        }
        return false;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        mEditor = e.getData(PlatformDataKeys.EDITOR);       //编辑器
        mProject = e.getData(PlatformDataKeys.PROJECT);     //项目
        mActivityFile = ((PsiJavaFile) e.getData(LangDataKeys.PSI_FILE));   //当前文件
        mDirectoryService = JavaDirectoryService.getInstance();             //文件夹服务类
        if (mEditor != null && mActivityFile != null && mProject != null) {
            mStyleManager = JavaCodeStyleManager.getInstance(mProject);     //代码管理类
            mPsiFileFactory = PsiFileFactory.getInstance(mProject);         //文件工厂
            mNamesCache = PsiShortNamesCache.getInstance(mProject);         //短类名缓存
            mElementFactory = JavaPsiFacade.getInstance(mProject).getElementFactory();  //代码元素工厂
            mTargetClass = getTargetClass(mEditor, mActivityFile);          //当前编辑类
            if (mTargetClass == null) {
                return;
            }
            //处理类名
            String startName = mTargetClass.getName();
            if (isActivity(mTargetClass)) {
                mIsActivity = true;
                mName = startName.replaceAll(UP_ACTIVITY, "").replaceAll(LOW_ACTIVITY, "");
            } else if (isFragment(mTargetClass)) {
                mIsActivity = false;
                mName = startName.replaceAll(UP_FRAGMENT, "").replaceAll(LOW_FRAGMENT, "");
            }
            //获取文件夹
            PsiDirectory currentDirectory = mActivityFile.getParent();
            if (currentDirectory != null) {
                PsiDirectory originalDirectory = getOriginalDirectory(currentDirectory);
                createAllInterAndImpl(originalDirectory);
            }
        }
    }

    /**
     * 创建所有的interface
     *
     * @param originalDirectory
     */
    private void createAllInterAndImpl(PsiDirectory originalDirectory) {
        WriteCommandAction.runWriteCommandAction(mProject, () -> {
            //创建inter
            PsiDirectory viewDirectory = createMvpDirectory(LOW_VIEW, originalDirectory);
            PsiClass viewInter = createViewInter(viewDirectory);
            PsiDirectory presenterDirectory = createMvpDirectory(LOW_PRESENTER, originalDirectory);
            PsiClass presenterInter = createPresenterInter(presenterDirectory);
            PsiDirectory modelDirectory = createMvpDirectory(LOW_MODEL, originalDirectory);
            PsiClass modelInter = createModelInter(modelDirectory);
            //移动当前文件
            moveTargetFile(viewInter, presenterInter);
        });
    }

    /**
     * 处理并移动当前文件
     *
     * @param viewInter
     * @param presenterInter
     */
    private void moveTargetFile(PsiClass viewInter, PsiClass presenterInter) {
        //实现view接口
        boolean hasImplements = false;
        PsiReferenceList implementsList = mTargetClass.getImplementsList();
        if (implementsList != null) {
            PsiJavaCodeReferenceElement[] implementsElements = implementsList.getReferenceElements();
            if (implementsElements.length > 0) {
                hasImplements = true;
                PsiJavaCodeReferenceElement viewInterElement = mElementFactory.createClassReferenceElement(viewInter);
                boolean hasTargetInter = false;
                for (PsiJavaCodeReferenceElement implementsElement : implementsElements) {
                    if (implementsElement.getQualifiedName().equals(viewInterElement.getQualifiedName())) {
                        hasTargetInter = true;
                        break;
                    }
                }
                if (!hasTargetInter) {
                    implementsList.add(viewInterElement);
                }
            }

            if (!hasImplements) {
                implementsList.add(mElementFactory.createClassReferenceElement(viewInter));
            }

            implMethods(viewInter);
        }


    }

    /**
     * 创建“\n”和“;”
     */
    private void createLineAndSemicolon() {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "class _Test_ {\nint a = 10;}";
        PsiFile testFile = mPsiFileFactory.createFileFromText("_Test_.java", type, content);
        testFile.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitJavaToken(PsiJavaToken token) {
                super.visitJavaToken(token);
                if (mSemicolon == null && token.getTokenType().equals(JavaTokenType.SEMICOLON)) {
                    mSemicolon = token;
                }
            }

            @Override
            public void visitWhiteSpace(PsiWhiteSpace space) {
                super.visitWhiteSpace(space);
                if (mWhiteSpace == null && space.getText().equals("\n")) {
                    mWhiteSpace = space;
                }
            }
        });
    }

    /**
     * 找当前类的onCreate方法
     *
     * @return
     */
    private PsiMethod getTargetClassOnCreate() {
        PsiMethod[] onCreates = mTargetClass.findMethodsByName("onCreate", false);
        for (PsiMethod onCreate : onCreates) {
            if (onCreate.getParameterList().getParameters().length == 1) {
                return onCreate;
            }
        }
        return null;
    }

    /**
     * 寻找onCreate方法
     *
     * @param psiClass
     * @return
     */
    private PsiMethod findOnCreateMethod(PsiClass psiClass) {
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
            PsiMethod[] onCreates = superClass.findMethodsByName("onCreate", false);
            for (PsiMethod onCreate : onCreates) {
                if (onCreate.getParameterList().getParameters().length == 1) {
                    return onCreate;
                }
            }
            findOnCreateMethod(superClass);
        }
        return null;
    }

    /**
     * 找当前类的initPresenter方法
     *
     * @return
     */
    private PsiMethod getTargetClassInitPresenter() {
        PsiMethod[] initPresenters = mTargetClass.findMethodsByName("initPresenter", false);
        for (PsiMethod initPresenter : initPresenters) {
            if (initPresenter.getParameterList().getParameters().length == 1) {
                return initPresenter;
            }
        }
        return null;
    }

    /**
     * 寻找initPresenter方法
     *
     * @param psiClass
     * @return
     */
    private PsiMethod findPresenterMethod(PsiClass psiClass) {
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
            PsiMethod[] initPresenters = superClass.findMethodsByName("initPresenter", false);
            for (PsiMethod initPresenter : initPresenters) {
                if (initPresenter.getParameterList().getParameters().length == 1) {
                    return initPresenter;
                }
            }
            findPresenterMethod(superClass);
        }
        return null;
    }

    /**
     * 复写inter方法
     *
     * @param viewInter
     */
    private void implMethods(PsiClass viewInter) {
        PsiMethod[] methods = viewInter.getMethods();
        for (PsiMethod method : methods) {
            List<PsiMethod> psiMethods = OverrideImplementUtil.overrideOrImplementMethod(mTargetClass, method, false);
            for (PsiMethod psiMethod : psiMethods) {
                mTargetClass.add(psiMethod);
            }
        }
    }

    /**
     * 获取包名
     *
     * @param psiClass
     */
    private String importClass(PsiClass psiClass) {
        PsiPackage directoryPackage = mDirectoryService.getPackage(psiClass.getContainingFile().getContainingDirectory());
        if (directoryPackage != null) {
            StringBuilder builder = new StringBuilder();
            builder.append("import ");
            builder.append(directoryPackage.getQualifiedName());
            builder.append(".");
            builder.append(psiClass.getName());
            builder.append(";");
            return builder.toString();
        }
        return null;
    }

    /**
     * 创建model的impl
     *
     * @param modelDirectory
     * @return
     */
    private PsiClass createModelImpl(PsiDirectory modelDirectory, String... packages) {
        PsiFile implFile = modelDirectory.findFile(getImplJavaFile(UP_MODEL));
        PsiClass implClass = null;
        if (implFile == null) {
            implClass = (PsiClass) modelDirectory.add(createImplJava(UP_MODEL, packages));
        } else {
            implClass = mNamesCache.getClassesByName(getImplJavaClass(UP_MODEL), modelDirectory.getResolveScope())[0];
        }
        return implClass;
    }

    /**
     * 创建presenter的impl
     *
     * @param presenterDirectory
     * @return
     */
    private PsiClass createPresenterImpl(PsiDirectory presenterDirectory, String... packages) {
        PsiFile implFile = presenterDirectory.findFile(getImplJavaFile(UP_PRESENTER));
        PsiClass implClass = null;
        if (implFile == null) {
            implClass = (PsiClass) presenterDirectory.add(createImplJava(UP_PRESENTER, packages));
        } else {
            implClass = mNamesCache.getClassesByName(getImplJavaClass(UP_PRESENTER), presenterDirectory.getResolveScope())[0];
        }
        return implClass;
    }

    /**
     * 创建impl的模板类
     *
     * @param mvp
     * @return
     */
    private PsiClass createImplJava(String mvp, String... packages) {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "";
        if (packages != null) {
            StringBuilder builder = new StringBuilder();
            for (String aPackage : packages) {
                builder.append(aPackage);
            }
            content = builder.toString();
        }
        switch (mvp) {
            case UP_PRESENTER:
                String interView = getInterJavaClass(UP_VIEW);
                String mInterView = " m" + interView;
                content += "public class " + mvp + " extends BasePresenter<" + interView + "," + getImplJavaClass(UP_MODEL) + ">{" + "public " + mvp +
                        "(" + interView + " " + mInterView + "){" + "setVM(" + mInterView + "," + "new " + getImplJavaClass(UP_MODEL) + "());" +
                        "}}";
                break;
            case UP_MODEL:
                content += "public class " + mvp + " implements BaseModel {}";
                break;
        }
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(getImplJavaFile(mvp), type, content)).getClasses()[0];
    }

    /**
     * 创建model的interface
     *
     * @param modelDirectory
     * @return
     */
    private PsiClass createModelInter(PsiDirectory modelDirectory) {

        PsiFile modelFile = modelDirectory.findFile(getInterJavaFile(UP_MODEL));
        PsiClass interJava = createModelInterJava(getInterJavaClass(UP_MODEL));
        PsiClass modelInter = null;
        if (modelFile == null) {
            modelInter = (PsiClass) modelDirectory.add(interJava);
        } else {
            modelInter = mNamesCache.getClassesByName(getInterJavaClass(UP_MODEL), modelDirectory.getResolveScope())[0];
        }
        return modelInter;
    }

    /**
     * 创建presenter的interface
     *
     * @param presenterDirectory
     * @return
     */
    private PsiClass createPresenterInter(PsiDirectory presenterDirectory) {
        PsiFile presenterFile = presenterDirectory.findFile(getInterJavaFile(UP_PRESENTER));
        PsiClass interJava = createPresenterOrModelInterJava(getInterJavaClass(UP_PRESENTER));
        PsiClass presenterInter = null;
        if (presenterFile == null) {
            presenterInter = (PsiClass) presenterDirectory.add(interJava);
        } else {
            presenterInter = mNamesCache.getClassesByName(getInterJavaClass(UP_PRESENTER), presenterDirectory.getResolveScope())[0];
        }
        return presenterInter;
    }


    /**
     * 创建view的interface
     *
     * @param viewDirectory
     */
    private PsiClass createViewInter(PsiDirectory viewDirectory) {
        PsiFile viewFile = viewDirectory.findFile(getInterJavaFile(UP_VIEW));
        PsiClass interJava = createViewInterJava(getInterJavaClass(UP_VIEW));
        PsiClass viewInter = null;
        if (viewFile == null) {
            viewInter = (PsiClass) viewDirectory.add(interJava);
        } else {
            viewInter = mNamesCache.getClassesByName(getInterJavaClass(UP_VIEW), viewDirectory.getResolveScope())[0];
        }
        return viewInter;
    }

    /**
     * 创建View的interface模板类
     *
     * @param name
     * @return
     */
    private PsiClass createViewInterJava(String name) {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "public interface " + name + " extends BaseView { \n" +
                "//请求XX接口成功\nvoid get" + name + "Suc(JavaBean bean);\n//请求XX接口失败\nvoid get" + name + "Fail(String message);" + "}";
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(name + ".java", type, content)).getClasses()[0];
    }

    /**
     * 创建presenter/model的interface模板类
     *
     * @param name
     * @return
     */
    private PsiClass createPresenterOrModelInterJava(String name) {
        JavaFileType type = JavaFileType.INSTANCE;
        String interView = getInterJavaClass(UP_VIEW);
        String mInterView = " m" + interView;
        String content = "public class " + name + " extends BasePresenter<" + interView + "," + getImplJavaClass(UP_MODEL) + ">{" + "public " + name +
                "(" + interView + " " + mInterView + "){" + "setVM(" + mInterView + "," + "new " + getImplJavaClass(UP_MODEL) + "());" +
                "}}";
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(name + ".java", type, content)).getClasses()[0];
    }

    /**
     * 创建model的interface模板类
     *
     * @param name
     * @return
     */
    private PsiClass createModelInterJava(String name) {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "public class " + name + " implements BaseModel {}";
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(name + ".java", type, content)).getClasses()[0];
    }

    /**
     * 获取根包
     *
     * @param currentDirectory
     * @return
     */
    private PsiDirectory getOriginalDirectory(PsiDirectory currentDirectory) {
        PsiPackage currentPackage = mDirectoryService.getPackage(currentDirectory);
        if (currentPackage != null) {
            if (currentPackage.getQualifiedName().contains("." + LOW_VIEW)) {
                return getOriginalDirectory(currentDirectory.getParent());
            }
        }
        return currentDirectory;
    }

    /**
     * 创建MVP包
     *
     * @param mvp
     * @param originalDirectory
     * @return
     */
    private PsiDirectory createMvpDirectory(String mvp, PsiDirectory originalDirectory) {
        PsiDirectory mvpDirectory = originalDirectory.findSubdirectory(mvp);
        if (mvpDirectory == null) {
            mvpDirectory = originalDirectory.createSubdirectory(mvp);
        }
        return mvpDirectory;
    }


    /**
     * 获取带有后缀名Java的inter文件
     *
     * @param mvp
     * @return
     */
    private String getInterJavaFile(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append(mName);
        builder.append(mvp);
        builder.append(".java");
        return builder.toString();
    }

    /**
     * 获取不带后缀名Java的inter类
     *
     * @param mvp
     * @return
     */
    private String getInterJavaClass(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append(mName);
        builder.append(mvp);
        return builder.toString();
    }

    /**
     * 获取带有后缀名Java的class文件
     *
     * @param mvp
     * @return
     */
    private String getImplJavaFile(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append(mName);
        builder.append(mvp);
        builder.append(".java");
        return builder.toString();
    }

    /**
     * 获取不带后缀名Java的class类
     *
     * @param mvp
     * @return
     */
    private String getImplJavaClass(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append(mName);
        builder.append(mvp);
        return builder.toString();
    }

}