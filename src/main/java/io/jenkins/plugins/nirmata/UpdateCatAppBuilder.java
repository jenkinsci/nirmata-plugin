
package io.jenkins.plugins.nirmata;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.google.common.base.Strings;

import hudson.Extension;
import hudson.model.Item;
import hudson.util.*;
import io.jenkins.plugins.nirmata.action.ActionType;
import io.jenkins.plugins.nirmata.model.*;
import io.jenkins.plugins.nirmata.util.*;
import jenkins.model.Jenkins;

public class UpdateCatAppBuilder extends ActionBuilder {

    private final String _catalog;
    private final Integer _timeout;
    private final String _directories;
    private final boolean _includescheck;
    private final String _includes;
    private final boolean _excludescheck;
    private final String _excludes;

    public String getCatalog() {
        return _catalog;
    }

    public Integer getTimeout() {
        return _timeout;
    }

    public String getDirectories() {
        return _directories;
    }

    public boolean isIncludescheck() {
        return _includescheck;
    }

    public String getIncludes() {
        return _includes;
    }

    public boolean isExcludescheck() {
        return _excludescheck;
    }

    public String getExcludes() {
        return _excludes;
    }

    @DataBoundConstructor
    public UpdateCatAppBuilder(String endpoint, String apikey, String catalog, Integer timeout, String directories,
        boolean includescheck, String includes, boolean excludescheck, String excludes) {
        super(endpoint, apikey);
        _catalog = catalog;
        _timeout = timeout;
        _directories = directories;
        _includescheck = includescheck;
        _includes = includes;
        _excludescheck = excludescheck;
        _excludes = excludes;
    }

    @Symbol("updateAppInCatalog")
    @Extension
    public static final class DescriptorImpl extends BuilderDescriptor {

        private NirmataCredentials credentials;

        public DescriptorImpl() {
            super(UpdateCatAppBuilder.class, ActionType.UPDATE_CAT_APP.toString());
        }

        @Override
        public String getDisplayName() {
            return ActionType.UPDATE_CAT_APP.toString();
        }

        public FormValidation doCheckEndpoint(@AncestorInPath Item project, @QueryParameter String endpoint) {
            credentials = new NirmataCredentials(project);

            if (!Strings.isNullOrEmpty(endpoint)) {
                NirmataClient client = new NirmataClient(endpoint, null);
                Status status = client.getEnvironments().getStatus();

                return (status != null && status.getStatusCode() != HttpServletResponse.SC_UNAUTHORIZED)
                    ? FormValidation.error(String.format("%s (%s)", status.getMessage(), status.getStatusCode()))
                    : FormValidation.ok();
            } else {
                return FormValidation.warning("Endpoint is required");
            }
        }

        public FormValidation doCheckApikey(@AncestorInPath Item project, @QueryParameter String endpoint,
            @QueryParameter String apikey) {
            if (!Strings.isNullOrEmpty(apikey) && credentials.getCredential(apikey).isPresent()) {
                Optional<StringCredentials> credential = credentials.getCredential(apikey);
                NirmataClient client = new NirmataClient(endpoint, credential.get().getSecret().getPlainText());
                Status status = client.getEnvironments().getStatus();

                return (status != null && status.getStatusCode() != HttpServletResponse.SC_OK)
                    ? FormValidation.error(String.format("%s (%s)", status.getMessage(), status.getStatusCode()))
                    : FormValidation.ok();
            } else {
                return FormValidation.warning("API key is required");
            }
        }

        @SuppressWarnings("deprecation")
        public ListBoxModel doFillApikeyItems(@AncestorInPath Item project, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (project == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!project.hasPermission(Item.EXTENDED_READ)
                    && !project.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }

            List<StringCredentials> stringCredentials = credentials.getCredentials();
            return result.includeEmptyValue().withAll(stringCredentials).includeCurrentValue(credentialsId);
        }

        public ListBoxModel doFillCatalogItems(@AncestorInPath Item project, @QueryParameter String endpoint,
            @QueryParameter String apikey) {
            ListBoxModel models = new ListBoxModel();
            if (Strings.isNullOrEmpty(endpoint) || Strings.isNullOrEmpty(apikey)) {
                return models;
            }

            Optional<StringCredentials> credential = credentials.getCredential(apikey);
            NirmataClient client = new NirmataClient(endpoint, credential.get().getSecret().getPlainText());
            List<Model> catalogApplications = client.getAppsFromCatalog().getModel();
            Status status = client.getAppsFromCatalog().getStatus();

            if (status.getStatusCode() == HttpServletResponse.SC_OK) {
                if (catalogApplications != null && !catalogApplications.isEmpty()) {
                    models.add(new ListBoxModel.Option("Select catalog", null, false));

                    for (Model model : catalogApplications) {
                        models.add(model.getName());
                    }
                } else {
                    models.add(new ListBoxModel.Option("--- No catalogs found ---", null, false));
                }
            }

            return models;
        }
    }

}