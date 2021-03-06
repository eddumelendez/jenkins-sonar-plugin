/*
 * Jenkins Plugin for SonarQube, open source software quality management tool.
 * mailto:contact AT sonarsource DOT com
 *
 * Jenkins Plugin for SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Jenkins Plugin for SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
/*
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package hudson.plugins.sonar;

import org.codehaus.plexus.util.StringUtils;
import hudson.plugins.sonar.utils.SonarUtils;
import hudson.model.Action;
import hudson.plugins.sonar.action.SonarMarkerAction;
import org.kohsuke.stapler.DataBoundConstructor;
import jenkins.model.Jenkins;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.plugins.sonar.utils.BuilderUtils;
import hudson.util.ArgumentListBuilder;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import hudson.tasks.Builder;

public class MsBuildSQRunnerEnd extends AbstractMsBuildSQRunner {
  @DataBoundConstructor
  public MsBuildSQRunnerEnd() {
    // will use MSBuild SQ Scanner installation defined in Begin
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
    ArgumentListBuilder args = new ArgumentListBuilder();

    EnvVars env = BuilderUtils.getEnvAndBuildVars(run, listener);
    String scannerName = loadScannerName(env);
    String sonarInstName = loadSonarInstanceName(env);
    SonarInstallation sonarInstallation = getSonarInstallation(sonarInstName, listener);

    MsBuildSQRunnerInstallation msBuildScanner = Jenkins.getInstance().getDescriptorByType(MsBuildSQRunnerBegin.DescriptorImpl.class)
      .getMsBuildScannerInstallation(scannerName);
    args.add(getExeName(msBuildScanner, env, launcher, listener));
    addArgs(args, env, sonarInstallation);

    int result = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(BuilderUtils.getModuleRoot(run, workspace)).join();

    if (result != 0) {
      addBadge(run, sonarInstallation);
      throw new AbortException(Messages.MSBuildScanner_ExecFailed(result));
    }

    addBadge(run, sonarInstallation);
  }

  private static void addArgs(ArgumentListBuilder args, EnvVars env, SonarInstallation sonarInstallation) throws IOException, InterruptedException {
    Map<String, String> props = getSonarProps(sonarInstallation);

    args.add("end");

    // expand macros using itself
    EnvVars.resolve(props);

    for (Map.Entry<String, String> e : props.entrySet()) {
      if (!StringUtils.isEmpty(e.getValue())) {
        // expand macros using environment variables and hide passwords/tokens
        boolean hide = e.getKey().contains("password") ||
          (!StringUtils.isEmpty(sonarInstallation.getServerAuthenticationToken()) && e.getKey().contains("login"));
        args.addKeyValuePair("/d:", e.getKey(), env.expand(e.getValue()), hide);
      }
    }

    args.addTokenized(sonarInstallation.getAdditionalProperties());
  }

  private static Map<String, String> getSonarProps(SonarInstallation inst) {
    Map<String, String> map = new LinkedHashMap<String, String>();

    if (!StringUtils.isBlank(inst.getServerAuthenticationToken())) {
      map.put("sonar.login", inst.getServerAuthenticationToken());
    } else {
      map.put("sonar.login", inst.getSonarLogin());
      map.put("sonar.password", inst.getSonarPassword());
    }

    map.put("sonar.jdbc.username", inst.getDatabaseLogin());
    map.put("sonar.jdbc.password", inst.getDatabasePassword());

    return map;
  }

  private static void addBadge(Run<?, ?> run, SonarInstallation sonarInstallation) throws IOException {
    SonarUtils.addBuildInfoTo(run, sonarInstallation.getName());
  }

  @Override
  public Action getProjectAction(AbstractProject<?, ?> project) {
    return new SonarMarkerAction();
  }

  @Extension
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    public String getHelpFile() {
      return "/plugin/sonar/help-ms-build-sq-scanner-end.html";
    }

    @Override
    public String getDisplayName() {
      return Messages.MsBuildScannerEnd_DisplayName();
    }
  }
}
