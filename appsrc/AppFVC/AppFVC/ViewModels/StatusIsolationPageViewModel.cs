﻿using Prism.Navigation;
using System;
using System.Threading.Tasks;
using Xamarin.Essentials;
using Xamarin.Forms;

namespace AppFVC.ViewModels
{
    public class StatusIsolationPageViewModel : ViewModelBase
    {
        private readonly INavigationService _navigationService;

        public Command NavegarSite { get; set; }
        public Command NavegarPaginaQuarentine { get; set; }
        public Command NavegarAtualiza { get; set; }
        public Command NavegarTel { get; set; }
        public StatusIsolationPageViewModel(INavigationService navigationService) :base(navigationService)
        {
            _navigationService = navigationService;
            NavegarSite = new Command(async () => await NavegarSiteCommand());
            NavegarPaginaQuarentine = new Command(async () => await NavegarPaginaCommand());
            NavegarAtualiza = new Command(async () => await NavegarAtualizaCommand());
            NavegarTel = new Command(async () => await NavegarTelCommand());
        }
        private async Task NavegarTelCommand()
        {
            PhoneDialer.Open("0800 333 3233");
        }

        private async Task NavegarAtualizaCommand()
        {

            var Url = new Uri("http://www.pmf.sc.gov.br/entidades/saude/index.php");

            Device.OpenUri(Url);
        }

        private async Task NavegarPaginaCommand()
        {
            await _navigationService.NavigateAsync("/StatusQuarantinePage");
        }

        private async Task NavegarSiteCommand()
        {
            var Url = new Uri("http://www.alosaudefloripa.com.br/");

            Device.OpenUri(Url);
        }
    }
}
