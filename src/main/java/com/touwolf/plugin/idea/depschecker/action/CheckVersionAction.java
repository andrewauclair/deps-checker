package com.touwolf.plugin.idea.depschecker.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.table.JBTable;
import com.touwolf.plugin.idea.depschecker.model.PomInfo;
import com.touwolf.plugin.idea.depschecker.ui.CheckVersionCellRenderer;
import com.touwolf.plugin.idea.depschecker.ui.CheckVersionTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class CheckVersionAction extends AnAction
{
    private static final String TOOL_WINDOW_ID = "check_version_action_tool_window_id";

    @Override
    public void actionPerformed(AnActionEvent event)
    {
        Project project = event.getProject();
        if (project == null)
        {
            return;
        }
        List<PomInfo> pomInfos = findPomInfos(project.getBaseDir());
        ToolWindowManager toolWindowMgr = ToolWindowManager.getInstance(project);
        ToolWindow tw = toolWindowMgr.getToolWindow(TOOL_WINDOW_ID);
        if (tw == null)
        {
            tw = toolWindowMgr.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM, true);
        }
        final ToolWindow toolWindow = tw;
        toolWindow.activate(() -> updateContent(toolWindow, pomInfos), true);
    }

    private List<PomInfo> findPomInfos(VirtualFile baseDir)
    {
        List<PomInfo> poms = new LinkedList<>();
        if (!baseDir.isDirectory() || baseDir.getName().startsWith("."))
        {
            return poms;
        }
        VirtualFile[] children = baseDir.getChildren();
        for (VirtualFile child : children)
        {
            if (child.isDirectory())
            {
                poms.addAll(findPomInfos(child));
            }
            else
            {
                PomInfo pom = parsePom(child);
                if (pom != null)
                {
                    poms.add(pom);
                }
            }
        }
        return poms;
    }

    private PomInfo parsePom(VirtualFile file)
    {
        if (file.isDirectory() || !"pom.xml".equals(file.getName()))
        {
            return null;
        }

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try
        {
            Model model = reader.read(file.getInputStream());
            Model parent = null;
            if (model.getParent() != null && file.getParent() != null && file.getParent().getParent() != null)
            {
                VirtualFile parentDirFile = file.getParent().getParent();
                VirtualFile parentFile = parentDirFile.findChild("pom.xml");
                if (parentFile != null)
                {
                    parent = reader.read(parentFile.getInputStream());
                }
            }
            return PomInfo.parse(model, parent);
        }
        catch (IOException | XmlPullParserException e)
        {
            return null;
        }
    }

    private void updateContent(ToolWindow toolWindow, List<PomInfo> pomInfos)
    {
        ContentManager contentManager = toolWindow.getContentManager();
        contentManager.removeAllContents(true);
        Content content = contentManager.getFactory().createContent(createContent(pomInfos), "TITLE", false);
        contentManager.addContent(content);
    }

    private JComponent createContent(List<PomInfo> pomInfos)
    {
        CheckVersionTableModel model = new CheckVersionTableModel(pomInfos);
        JBTable table = new JBTable(model);
        table.setStriped(true);
        table.setDefaultRenderer(Object.class, new CheckVersionCellRenderer());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(table.getTableHeader(), BorderLayout.PAGE_START);
        panel.add(table, BorderLayout.CENTER);
        return panel;
    }
}