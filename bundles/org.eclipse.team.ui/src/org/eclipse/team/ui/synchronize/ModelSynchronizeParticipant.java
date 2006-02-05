/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.ui.synchronize;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.resources.mapping.*;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.mapping.*;
import org.eclipse.team.core.mapping.provider.SynchronizationScopeManager;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.mapping.ModelProviderAction;
import org.eclipse.team.internal.ui.mapping.ModelSynchronizePage;
import org.eclipse.team.internal.ui.synchronize.*;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.mapping.*;
import org.eclipse.ui.*;
import org.eclipse.ui.part.IPageBookViewPage;

/**
 * Synchronize participant that obtains it's synchronization state from
 * a {@link ISynchronizationContext}.
 * <p>
 * This class may be subclassed by clients
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @since 3.2
 */
public class ModelSynchronizeParticipant extends
		AbstractSynchronizeParticipant implements ISaveableModelSource {
	
	public static final String PROP_CURRENT_MODEL = TeamUIPlugin.ID + ".CURRENT_MODEL"; //$NON-NLS-1$
	
	public static final String PROP_DIRTY = TeamUIPlugin.ID + ".DIRTY"; //$NON-NLS-1$
	
	/*
	 * Key for settings in memento
	 */
	private static final String CTX_PARTICIPANT_SETTINGS = TeamUIPlugin.ID + ".MODEL_PARTICIPANT_SETTINGS"; //$NON-NLS-1$
	
	/*
	 * Key for schedule in memento
	 */
	private static final String CTX_REFRESH_SCHEDULE_SETTINGS = TeamUIPlugin.ID + ".MODEL_PARTICIPANT_REFRESH_SCHEDULE"; //$NON-NLS-1$

	/*
	 * Key for description in memento
	 */
	private static final String CTX_DESCRIPTION = TeamUIPlugin.ID + ".MODEL_PARTICIPANT_DESCRIPTION"; //$NON-NLS-1$
	
	/*
	 * Constants used to save and restore this scope
	 */
	private static final String CTX_PARTICIPANT_MAPPINGS = TeamUIPlugin.ID + ".MODEL_PARTICIPANT_MAPPINGS"; //$NON-NLS-1$
	private static final String CTX_MODEL_PROVIDER_ID = "modelProviderId"; //$NON-NLS-1$
	private static final String CTX_MODEL_PROVIDER_MAPPINGS = "mappings"; //$NON-NLS-1$
	
	private ISynchronizationContext context;
	private ISynchronizationScopeManager manager;
	private boolean mergingEnabled = true;
	protected SubscriberRefreshSchedule refreshSchedule;
	private String description;
	private ISaveableCompareModel currentModel;

	private IPropertyListener dirtyListener = new IPropertyListener() {
		public void propertyChanged(Object source, int propId) {
			if (source instanceof ISaveableCompareModel && propId == ISaveableCompareModel.PROP_DIRTY) {
				ISaveableCompareModel scm = (ISaveableCompareModel) source;
				boolean isDirty = scm.isDirty();
				firePropertyChange(ModelSynchronizeParticipant.this, PROP_DIRTY, Boolean.valueOf(!isDirty), Boolean.valueOf(isDirty));
			}
		}
	};

	/**
	 * Create a participant for the given context
	 * @param manager the scope manager
	 * @param context the synchronization context
	 * @param name the name of the participant
	 */
	public static ModelSynchronizeParticipant createParticipant(ISynchronizationScopeManager manager, ISynchronizationContext context, String name) {
		return new ModelSynchronizeParticipant(manager, context, name);
	}
	
	/*
	 * Create a participant for the given context
	 * @param context the synchronization context
	 * @param name the name of the participant
	 */
	private ModelSynchronizeParticipant(ISynchronizationScopeManager manager, ISynchronizationContext context, String name) {
		this.manager = manager;
		initializeContext(context);
		try {
			setInitializationData(TeamUI.getSynchronizeManager().getParticipantDescriptor("org.eclipse.team.ui.synchronization_context_synchronize_participant")); //$NON-NLS-1$
		} catch (CoreException e) {
			TeamUIPlugin.log(e);
		}
		setSecondaryId(Long.toString(System.currentTimeMillis()));
		setName(name);
		refreshSchedule = new SubscriberRefreshSchedule(createRefreshable());
	}

	/**
	 * Create a participant for the given context
	 * @param context the synchronization context
	 */
	public ModelSynchronizeParticipant(ISynchronizationScopeManager manager, ISynchronizationContext context) {
		this.manager = manager;
		initializeContext(context);
		refreshSchedule = new SubscriberRefreshSchedule(createRefreshable());
	}
	
	/**
	 * Create a participant in order to restore it from saved state.
	 */
	public ModelSynchronizeParticipant() {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.AbstractSynchronizeParticipant#getName()
	 */
	public String getName() {
		String name = super.getName();
		if (description == null)
			description = Utils.getScopeDescription(getContext().getScope());
		return NLS.bind(TeamUIMessages.SubscriberParticipant_namePattern, new String[] { name, description }); 
	}

	/**
	 * Return the name of the participant as specified in the plugin manifest file. 
	 * This method is provided to give access to this name since it is masked by
	 * the <code>getName()</code> method defined in this class.
	 * @return the name of the participant as specified in the plugin manifest file
	 * @since 3.1
	 */
	protected final String getShortName() {
	    return super.getName();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.AbstractSynchronizeParticipant#initializeConfiguration(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
	 */
	protected void initializeConfiguration(
			ISynchronizePageConfiguration configuration) {
		if (isMergingEnabled()) {
			// The contetx menu groups are defined by the org.eclipse.ui.navigator.viewer extension
			configuration.addMenuGroup(ISynchronizePageConfiguration.P_TOOLBAR_MENU, ModelSynchronizeParticipantActionGroup.MERGE_ACTION_GROUP);
			configuration.addActionContribution(createMergeActionGroup());
		}
		configuration.setSupportedModes(ISynchronizePageConfiguration.ALL_MODES);
		configuration.setMode(ISynchronizePageConfiguration.BOTH_MODE);
		configuration.setProperty(ISynchronizationConstants.P_SYNCHRONIZATION_CONTEXT, getContext());
		configuration.setProperty(ISynchronizationConstants.P_RESOURCE_MAPPING_SCOPE, getContext().getScope());
	}

	/**
	 * Create the merge action group for this participant.
	 * Subclasses can override in order to provide a 
	 * merge action group that configures certain aspects
	 * of the merge actions.
	 * @return the merge action group for this participant
	 */
	protected ModelSynchronizeParticipantActionGroup createMergeActionGroup() {
		return new ModelSynchronizeParticipantActionGroup();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#createPage(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
	 */
	public final IPageBookViewPage createPage(
			ISynchronizePageConfiguration configuration) {
		return new ModelSynchronizePage(configuration);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#run(org.eclipse.ui.IWorkbenchPart)
	 */
	public void run(IWorkbenchPart part) {
		refresh(part != null ? part.getSite() : null, new ResourceMapping[0]);
	}

	/**
	 * Refresh a participant in the background the result of the refresh are shown in the progress view. Refreshing 
	 * can also be considered synchronizing, or refreshing the synchronization state. Basically this is a long
	 * running operation that will update the participant's context with new changes detected on the
	 * server. Passing an empty array of resource mappings will refresh all mappings in the context.
	 * 
	 * @param site the workbench site the synchronize is running from. This can be used to notify the site
	 * that a job is running.
	 * @param mappings the resource mappings to be refreshed
	 */
	public final void refresh(IWorkbenchSite site, ResourceMapping[] mappings) {
		IRefreshSubscriberListener listener = new RefreshUserNotificationPolicy(this);
		internalRefresh(mappings, null, null, site, listener);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#dispose()
	 */
	public void dispose() {
		manager.dispose();
		context.dispose();
		Platform.getJobManager().cancel(this);
		refreshSchedule.dispose();
	}
	
	/**
	 * Set the context of this participant. This method must be invoked
	 * before a page is obtained from the participant.
	 * @param context the context for this participant
	 */
	protected void initializeContext(ISynchronizationContext context) {
		this.context = context;
		mergingEnabled = context instanceof IMergeContext;
	}

	/**
	 * Return the synchronization context for this participant.
	 * @return the synchronization context for this participant
	 */
	public ISynchronizationContext getContext() {
		return context;
	}
	
	/**
	 * Return a compare input for the given model object or <code>null</code>
	 * if the object is not eligible for comparison.
	 * @param object the model object
	 * @return a compare input for the model object or <code>null</code>
	 */
	public ICompareInput asCompareInput(Object object) {
		if (object instanceof ICompareInput) {
			return (ICompareInput) object;
		}
		// Get a compare input from the model provider's compare adapter
		ISynchronizationCompareAdapter adapter = Utils.getCompareAdapter(object);
		if (adapter != null)
			return adapter.asCompareInput(getContext(), object);
		return null;
	}

	/**
	 * Return whether their is a compare input associated with the given object.
	 * In otherwords, return <code>true</code> if {@link #asCompareInput(Object) }
	 * would return a value and <code>false</code> if it would return <code>null</code>.
	 * @param object the object.
	 * @return whether their is a compare input associated with the given object
	 */
	public boolean hasCompareInputFor(Object object) {
		// Get a content viewer from the model provider's compare adapter
		ISynchronizationCompareAdapter adapter = Utils.getCompareAdapter(object);
		if (adapter != null)
			return adapter.hasCompareInput(getContext(), object);
		return false;
	}

	/**
	 * Return whether merge capabilities are enabled for this participant.
	 * If merging is enabled, merge actions can be shown. If merging is disabled, no 
	 * merge actions should be surfaced.
	 * @return whether merge capabilities should be enabled for this participant
	 */
	public boolean isMergingEnabled() {
		return mergingEnabled;
	}

	/**
	 * Set whether merge capabilities should be enabled for this participant.
	 * @param mergingEnabled whether merge capabilities should be enabled for this participant
	 */
	public void setMergingEnabled(boolean mergingEnabled) {
		this.mergingEnabled = mergingEnabled;
	}
	
	private void internalRefresh(ResourceMapping[] mappings, String jobName, String taskName, IWorkbenchSite site, IRefreshSubscriberListener listener) {
		if (jobName == null)
		    jobName = getShortTaskName();
		if (taskName == null)
		    taskName = getLongTaskName(mappings);
		Platform.getJobManager().cancel(this);
		RefreshParticipantJob job = new RefreshModelParticipantJob(this, jobName, taskName, mappings, listener);
		job.setUser(true);
		Utils.schedule(job, site);
		
		// Remember the last participant synchronized
		TeamUIPlugin.getPlugin().getPreferenceStore().setValue(IPreferenceIds.SYNCHRONIZING_DEFAULT_PARTICIPANT, getId());
		TeamUIPlugin.getPlugin().getPreferenceStore().setValue(IPreferenceIds.SYNCHRONIZING_DEFAULT_PARTICIPANT_SEC_ID, getSecondaryId());
	}
	
	/**
	 * Returns the short task name (e.g. no more than 25 characters) to describe
	 * the behavior of the refresh operation to the user. This is typically
	 * shown in the status line when this participant is refreshed in the
	 * background. When refreshed in the foreground, only the long task name is
	 * shown.
	 * 
	 * @return the short task name to show in the status line.
	 */
	protected String getShortTaskName() {
		return TeamUIMessages.Participant_synchronizing; 
	}
	
	/**
	 * Returns the long task name to describe the behavior of the refresh
	 * operation to the user. This is typically shown in the status line when
	 * this subscriber is refreshed in the background.
	 * 
	 * @param mappings the mappings being refreshed
	 * @return the long task name
	 * @since 3.1
	 */
    protected String getLongTaskName(ResourceMapping[] mappings) {
        if (mappings == null || (mappings.length == getContext().getScope().getMappings().length)) {
            // Assume we are refrshing everything
            return NLS.bind(TeamUIMessages.Participant_synchronizingDetails, new String[] { getName() }); 
        }
        int mappingCount = mappings.length;
        if (mappingCount == 1) {
            return NLS.bind(TeamUIMessages.Participant_synchronizingMoreDetails, new String[] { getShortName(), Utils.getLabel(mappings[0]) }); 
        }
        return NLS.bind(TeamUIMessages.Participant_synchronizingResources, new String[] { getShortName(), Integer.toString(mappingCount) }); 
    }
	
	private IRefreshable createRefreshable() {
		return new IRefreshable() {
		
			public RefreshParticipantJob createJob(String interval) {
				return new RefreshModelParticipantJob(ModelSynchronizeParticipant.this, 
						TeamUIMessages.RefreshSchedule_14, 
						NLS.bind(TeamUIMessages.RefreshSchedule_15, new String[] { ModelSynchronizeParticipant.this.getName(), interval }),
						new ResourceMapping[0],
						new RefreshUserNotificationPolicy(ModelSynchronizeParticipant.this));
			}
			public ISynchronizeParticipant getParticipant() {
				return ModelSynchronizeParticipant.this;
			}
			public void setRefreshSchedule(SubscriberRefreshSchedule schedule) {
				ModelSynchronizeParticipant.this.setRefreshSchedule(schedule);
			}
			public SubscriberRefreshSchedule getRefreshSchedule() {
				return refreshSchedule;
			}
		
		};
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.PlatformObject#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (adapter == IRefreshable.class && refreshSchedule != null) {
			return refreshSchedule.getRefreshable();
			
		}
		return super.getAdapter(adapter);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#saveState(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento memento) {
		super.saveState(memento);
		IMemento settings = memento.createChild(CTX_PARTICIPANT_SETTINGS);
		if (description != null)
			settings.putString(CTX_DESCRIPTION, description);
		refreshSchedule.saveState(settings.createChild(CTX_REFRESH_SCHEDULE_SETTINGS));
		saveMappings(settings);
	}

	private void saveMappings(IMemento settings) {
		ISynchronizationScope inputScope = getScopeManager().getScope().asInputScope();
		ModelProvider[] providers = inputScope.getModelProviders();
		for (int i = 0; i < providers.length; i++) {
			ModelProvider provider = providers[i];
			ISynchronizationCompareAdapter adapter = (ISynchronizationCompareAdapter)Utils.getAdapter(provider, ISynchronizationCompareAdapter.class);
			if (adapter != null) {
				IMemento child = settings.createChild(CTX_PARTICIPANT_MAPPINGS);
				String id = provider.getDescriptor().getId();
				child.putString(CTX_MODEL_PROVIDER_ID, id);
				adapter.save(inputScope.getMappings(id), child.createChild(CTX_MODEL_PROVIDER_MAPPINGS));
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#init(org.eclipse.ui.IMemento)
	 */
	public void init(String secondaryId, IMemento memento) throws PartInitException {
		super.init(secondaryId, memento);
		if(memento != null) {
			IMemento settings = memento.getChild(CTX_PARTICIPANT_SETTINGS);
			ResourceMapping[] mappings = loadMappings(settings);
			if (mappings.length == 0)
				throw new PartInitException(NLS.bind("{0} failed to initialize due to missing data during restore.", getId()));
			initializeContext(mappings);
			if(settings != null) {
				SubscriberRefreshSchedule schedule = SubscriberRefreshSchedule.init(settings.getChild(CTX_REFRESH_SCHEDULE_SETTINGS), createRefreshable());
				description = settings.getString(CTX_DESCRIPTION);
				setRefreshSchedule(schedule);
			}
		}
	}
	
	private ResourceMapping[] loadMappings(IMemento settings) throws PartInitException {
		List result = new ArrayList();
		IMemento[] children = settings.getChildren(CTX_PARTICIPANT_MAPPINGS);
		for (int i = 0; i < children.length; i++) {
			IMemento memento = children[i];
			String id = memento.getString(CTX_MODEL_PROVIDER_ID);
			if (id != null) {
				IModelProviderDescriptor desc = ModelProvider.getModelProviderDescriptor(id);
				try {
					ModelProvider provider = desc.getModelProvider();
					ISynchronizationCompareAdapter adapter = (ISynchronizationCompareAdapter)Utils.getAdapter(provider, ISynchronizationCompareAdapter.class);
					if (adapter != null) {
						ResourceMapping[] mappings = adapter.restore(memento.getChild(CTX_MODEL_PROVIDER_MAPPINGS));
						for (int j = 0; j < mappings.length; j++) {
							ResourceMapping mapping = mappings[j];
							result.add(mapping);
						}
					}
				} catch (CoreException e) {
					TeamUIPlugin.log(e);
				}
			}
		}
		return (ResourceMapping[]) result.toArray(new ResourceMapping[result.size()]);
	}

	private void initializeContext(ResourceMapping[] mappings) throws PartInitException {
		try {
			manager = createScopeManager(mappings);
			IMergeContext context = restoreContext(manager);
			initializeContext(context);
		} catch (CoreException e) {
			TeamUIPlugin.log(e);
			throw new PartInitException(e.getStatus());
		}
	}

	/**
	 * Recreate the context for this participant. This method is invoked when
	 * the participant is restored after a restart. Although it is provided
	 * with a progress monitor, long running operations should be avoided.
	 * @param manager the restored scope
	 * @return the context for this participant
	 * @throws CoreException
	 */
	protected IMergeContext restoreContext(ISynchronizationScopeManager manager) throws CoreException {
		throw new PartInitException(NLS.bind("Participant {0} is not capable of restoring its context", getId()));
	}

	/**
	 * Create and return a scope manager that can be used to build the scope of this
	 * participant when it is restored after a restart. By default, this method 
	 * returns a scope manager that uses the local content.
	 * This method can be overridden by subclasses.
	 * 
	 * @param mappings the restored mappings
	 * @return a scope manager that can be used to build the scope of this
	 * participant when it is restored after a restart
	 */
	protected ISynchronizationScopeManager createScopeManager(ResourceMapping[] mappings) {
		return new SynchronizationScopeManager(mappings, ResourceMappingContext.LOCAL_CONTEXT, true);
	}
	
	/* private */ void setRefreshSchedule(SubscriberRefreshSchedule schedule) {
		if (refreshSchedule != schedule) {
			if (refreshSchedule != null) {
				refreshSchedule.dispose();
			}
	        this.refreshSchedule = schedule;
		}
		// Always fir the event since the schedule may have been changed
        firePropertyChange(this, AbstractSynchronizeParticipant.P_SCHEDULED, schedule, schedule);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveableModelSource#getModels()
	 */
	public ISaveableModel[] getModels() {
		if (currentModel == null)
			return new ISaveableModel[0];
		return new ISaveableModel[] { currentModel };
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveableModelSource#getActiveModels()
	 */
	public ISaveableModel[] getActiveModels() {
		return getModels();
	}

	public ISaveableCompareModel getCurrentModel() {
		return currentModel;
	}

	public void setCurrentModel(ISaveableCompareModel currentModel) {
		boolean wasDirty = false;
		ISaveableCompareModel oldModel = this.currentModel;
		if (oldModel != null) {
			oldModel.removePropertyListener(dirtyListener);
			wasDirty = oldModel.isDirty();
		}
		this.currentModel = currentModel;
		firePropertyChange(this, PROP_CURRENT_MODEL, oldModel, currentModel);
		boolean isDirty = false;
		if (currentModel != null) {
			currentModel.addPropertyListener(dirtyListener);
			isDirty = currentModel.isDirty();
		}
		if (isDirty != wasDirty)
			firePropertyChange(this, PROP_DIRTY, Boolean.valueOf(wasDirty), Boolean.valueOf(isDirty));
	}
	
	public boolean checkForBufferChange(Shell shell, IModelCompareInput input, boolean cancelAllowed, IProgressMonitor monitor) throws CoreException {
		ISaveableCompareModel currentBuffer = getCurrentModel();
		ISaveableCompareModel targetBuffer = input.getCompareModel();
		try {
			ModelProviderAction.handleBufferChange(shell, targetBuffer, currentBuffer, cancelAllowed, Policy.subMonitorFor(monitor, 10));
		} catch (InterruptedException e) {
			return false;
		}
		setCurrentModel(targetBuffer);
		return true;
	}

	public ISynchronizationScopeManager getScopeManager() {
		return manager;
	}
	
}