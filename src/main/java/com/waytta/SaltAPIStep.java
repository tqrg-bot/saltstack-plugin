package com.waytta;

import com.waytta.SaltException;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

import com.google.common.collect.ImmutableSet;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.waytta.clientinterface.BasicClient;

public class SaltAPIStep extends Step {
    private static final Logger LOGGER = Logger.getLogger("com.waytta.saltstack");

    private String servername;
    private String authtype;
    private BasicClient clientInterface;
    private boolean saveEnvVar = false;
    private final String credentialsId;
    private boolean saveFile = false;

    @DataBoundConstructor
    public SaltAPIStep(String servername, String authtype, BasicClient clientInterface, String credentialsId) {
        this.servername = servername;
        this.authtype = authtype;
        this.clientInterface = clientInterface;
        this.credentialsId = credentialsId;
    }

    public String getServername() {
        return servername;
    }

    public String getAuthtype() {
        return authtype;
    }

    public String getTarget() {
        return clientInterface.getTarget();
    }

    public String getTargettype() {
        return clientInterface.getTargettype();
    }

    public String getFunction() {
        return clientInterface.getFunction();
    }

    public String getArguments() {
        return clientInterface.getArguments();
    }

    public boolean getBlockbuild() {
        return clientInterface.getBlockbuild();
    }

    public String getBatchSize() {
        return clientInterface.getBatchSize();
    }

    public int getJobPollTime() {
        return clientInterface.getJobPollTime();
    }

    public int getMinionTimeout() {
        return clientInterface.getMinionTimeout();
    }

    public String getMods() {
        return clientInterface.getMods();
    }

    public String getPillarvalue() {
        return clientInterface.getPillarvalue();
    }

    public String getSubset() {
        return clientInterface.getSubset();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setSaveEnvVar(boolean saveEnvVar) {
        this.saveEnvVar = saveEnvVar;
    }

    public boolean getSaveEnvVar() {
        return saveEnvVar;
    }

    @DataBoundSetter
    public void setSaveFile(boolean saveFile) {
        this.saveFile = saveFile;
    }

    public boolean getSaveFile() {
        return saveFile;
    }

    public BasicClient getClientInterface() {
        return clientInterface;
    }

    public String getPost() {
        return clientInterface.getPost();
    }

    public String getTag() {
        return clientInterface.getTag();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "salt";
        }

        @Override
        public String getDisplayName() {
            return "Send a message to Salt API";
        }

        public FormValidation doCheckServername(@QueryParameter String value) {
            return SaltAPIBuilder.DescriptorImpl.doCheckServername(value);
        }

        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Job context,
                @QueryParameter final String credentialsId,
                @QueryParameter final String servername) {
            return SaltAPIBuilder.DescriptorImpl.doFillCredentialsIdItems(context, credentialsId, servername);
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item project, @QueryParameter String value) {
            return SaltAPIBuilder.DescriptorImpl.doCheckCredentialsId(project, value);
        }

        public FormValidation doTestConnection(
                @QueryParameter String servername,
                @QueryParameter String credentialsId,
                @QueryParameter String authtype,
                @AncestorInPath Item project) {
            return SaltAPIBuilder.DescriptorImpl.doTestConnection(servername, credentialsId, authtype, project);
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class, Launcher.class);
        }
    }

    public static class SaltAPIStepExecution extends AbstractStepExecutionImpl {
        @Inject
        private transient SaltAPIStep saltStep;
        private volatile BodyExecution body;
        private JSONArray returnArray = null;

        protected String run() throws Exception, SaltException {
            return "";
            /*

            // Check for error and print out results
            boolean validFunctionExecution = Utils.validateFunctionCall(returnArray);
            if (!validFunctionExecution) {
                listener.error("One or more minion did not return code 0\n");
                throw new SaltException(returnArray.toString());
            }

            if (saltStep.saveFile) {
                Utils.writeFile(returnArray.toString(), workspace);
            }

            return returnArray.toString();
             */
        }

        private static final long serialVersionUID = 1L;

        @Override
        public boolean start() throws Exception {
            Run<?, ?>run = getContext().get(Run.class);
            FilePath workspace = getContext().get(FilePath.class);
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);

            SaltAPIBuilder saltBuilder = new SaltAPIBuilder(saltStep.servername, saltStep.authtype, saltStep.clientInterface, saltStep.credentialsId);

            StandardUsernamePasswordCredentials credential = CredentialsProvider.findCredentialById(
                    saltBuilder.getCredentialsId(), StandardUsernamePasswordCredentials.class, run);
            if (credential == null) {
                throw new RuntimeException("Invalid credentials");
            }

            // Setup connection for auth
            JSONObject auth = Utils.createAuthArray(credential, saltBuilder.getAuthtype());

            // Get an auth token
            ServerToken serverToken = Utils.getToken(launcher, saltBuilder.getServername(), auth);
            String token = serverToken.getToken();
            String netapi = serverToken.getServer();
            LOGGER.log(Level.FINE, "Discovered netapi: " + netapi);

            // If we got this far, auth must have been good and we've got a token
            JSONObject saltFunc = saltBuilder.prepareSaltFunction(run, listener, saltBuilder.getClientInterface().getDescriptor().getDisplayName(), saltBuilder.getTarget(), saltBuilder.getFunction(), saltBuilder.getArguments());
            LOGGER.log(Level.FINE, "Sending JSON: " + saltFunc.toString());
            //JSONArray returnArray = saltBuilder.performRequest(launcher, run, token, saltBuilder.getServername(), saltFunc, listener, netapi);
            //LOGGER.log(Level.FINE, "Received response: " + returnArray);

            body = getContext().newBodyInvoker().
                    withContext(saltBuilder.performRequest(launcher, run, token, saltBuilder.getServername(), saltFunc, listener, netapi)).
                    withCallback(BodyExecutionCallback.wrap(getContext())).
                    start();
            returnArray = saltBuilder.getReturnArray();

            return false;
        }

        @Override
        public void stop(Throwable cause) throws Exception {
            if (body != null) {
                body.cancel(cause);
            }
            if (returnArray != null) {
                getContext().onFailure(cause);
            }
        }
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SaltAPIStepExecution();
    }

}